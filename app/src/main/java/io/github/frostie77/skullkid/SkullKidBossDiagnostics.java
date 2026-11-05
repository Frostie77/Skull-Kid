package io.github.frostie77.skullkid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Debug-only in-memory Flash timeline markers. No original SWF data is committed. */
final class SkullKidBossDiagnostics {
    private static final int LIMIT = 40_000_000;
    private static final class Mark {
        final int offset, lenAt, spriteLenAt;
        final String label;
        Mark(int off, int lenAt, int spriteLenAt, String label) {
            this.offset=off;this.lenAt=lenAt;this.spriteLenAt=spriteLenAt;this.label=label;
        }
    }
    private SkullKidBossDiagnostics() {}

    private static int u16(byte[] a, int p) {
        return (a[p]&255)|((a[p+1]&255)<<8);
    }
    private static int u32(byte[] a, int p) {
        return u16(a,p)|(u16(a,p+2)<<16);
    }
    private static void put32(byte[] a, int p, int n) {
        for (int i=0;i<4;i++) a[p+i]=(byte)(n >>> (i*8));
    }
    private static byte[] trace(String marker) {
        byte[] str=marker.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        o.write(0x96);o.write((str.length+2)&255);o.write((str.length+2)>>8);
        o.write(0);o.write(str,0,str.length);o.write(0);o.write(0x26);
        return o.toByteArray();
    }
    private static void parseTags(byte[] b, int start, int end, int sprite,
                                  int spriteLenAt, List<Mark> marks) throws IOException {
        int frame=1;
        for(int p=start;p+2<=end;) {
            int h=u16(b,p),type=h>>>6,len=h&63,data=p+2;
            int lenAt=-1;
            if(len==63) {
                if(data+4>end)throw new IOException("Bad SWF long tag");
                lenAt=data;len=u32(b,data);data+=4;
            }
            if(len<0||len>end-data)throw new IOException("Bad SWF tag size");
            if(type==12 && lenAt>=0) {
                String marker=null;
                if(sprite==0 && frame==11)marker="ROOT_11_GAMEPLAY";
                if(sprite==0 && frame==12)marker="ROOT_12_CHECK";
                if(sprite==0 && frame==13)marker="ROOT_13_VICTORY";
                if(sprite==505 && frame==46)marker="BOSS_46_GORE_START";
                if(sprite==505 && frame==50)marker="BOSS_50_STOP";
                if(sprite==459 && frame==60)marker="GORE_60_HANDOFF";
                if(marker!=null)marks.add(new Mark(data,lenAt,spriteLenAt,marker));
            }
            if(type==39 && sprite==0 && len>=4) {
                int id=u16(b,data);
                if(id==459||id==505) {
                    if(lenAt<0)throw new IOException("Unexpected short sprite tag");
                    parseTags(b,data+4,data+len,id,lenAt,marks);
                }
            }
            if(type==1)frame++;
            if(type==0)break;
            p=data+len;
        }
    }
    static byte[] inject(byte[] input) throws IOException {
        if(input.length<21||input.length>LIMIT||input[1]!='W'||input[2]!='S'||
           (input[0]!='C'&&input[0]!='F'))return input;
        byte[] raw;
        if(input[0]=='F')raw=input.clone();
        else {
            ByteArrayOutputStream o=new ByteArrayOutputStream(input.length*2);
            o.write(input,0,8);
            try(InflaterInputStream in=new InflaterInputStream(
                 new ByteArrayInputStream(input,8,input.length-8))) {
                byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1) {
                    if(o.size()+n>LIMIT)throw new IOException("SWF inflated too far");
                    o.write(chunk,0,n);
                }
            }
            raw=o.toByteArray();raw[0]='F';
        }
        if(u32(raw,4)!=raw.length)throw new IOException("SWF length mismatch");
        int bits=(raw[8]&255)>>>3;
        int start=8+(5+4*bits+7)/8+4;
        List<Mark> marks=new ArrayList<>();
        parseTags(raw,start,raw.length,0,-1,marks);
        if(marks.size()!=6)return input; // Unknown game variants remain unchanged.
        marks.sort(Comparator.comparingInt((Mark m)->m.offset).reversed());
        for(Mark mark:marks) {
            byte[] marker=trace("SKULLDBG_"+mark.label);
            byte[] updated=new byte[raw.length+marker.length];
            System.arraycopy(raw,0,updated,0,mark.offset);
            System.arraycopy(marker,0,updated,mark.offset,marker.length);
            System.arraycopy(raw,mark.offset,updated,mark.offset+marker.length,
                raw.length-mark.offset);
            put32(updated,mark.lenAt,u32(raw,mark.lenAt)+marker.length);
            if(mark.spriteLenAt>=0)
                put32(updated,mark.spriteLenAt,
                    u32(raw,mark.spriteLenAt)+marker.length);
            raw=updated;
        }
        put32(raw,4,raw.length);
        if(input[0]=='F')return raw;
        ByteArrayOutputStream compressed=new ByteArrayOutputStream(input.length+4096);
        compressed.write(raw,0,8);
        try(DeflaterOutputStream out=new DeflaterOutputStream(compressed,
                new Deflater(Deflater.BEST_COMPRESSION))) {
            out.write(raw,8,raw.length-8);
        }
        byte[] packed=compressed.toByteArray();packed[0]='C';return packed;
    }
}
