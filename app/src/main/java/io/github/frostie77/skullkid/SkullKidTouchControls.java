package io.github.frostie77.skullkid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * Skull Kid's game-specific touchscreen controls.
 * The same held-key bridge used by physical inputs keeps multi-touch working.
 */
public final class SkullKidTouchControls extends View {
    public interface Input { void key(String key, boolean down); }

    private final Input input;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean[] pressed = new boolean[5]; // left, right, up, down, attack
    private static final String[] KEYS = {"left", "right", "up", "down", "attack"};
    private int movePointer = -1, actionPointer = -1;
    private float movementX, movementY, actionX, actionY, radius;
    private float thumbX, thumbY; // normalized joystick direction
    private int sizeIndex = 1, opacityIndex = 1, padMode, positionIndex = 1;
    private boolean swapped;

    public SkullKidTouchControls(Context context, Input input) {
        super(context);
        this.input = input;
        setClickable(true);
    }

    public void configure(int size, int opacity, int pad, boolean swapped, int position) {
        this.sizeIndex = Math.max(0, Math.min(2, size));
        this.opacityIndex = Math.max(0, Math.min(2, opacity));
        this.padMode = pad == 1 ? 1 : 0;
        this.swapped = swapped;
        this.positionIndex = Math.max(0, Math.min(2, position));
        invalidate();
    }

    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }

    private void computeLayout() {
        float scale = new float[]{0.84f, 1f, 1.16f}[sizeIndex];
        float inset = dp(new float[]{14, 26, 42}[positionIndex]);
        radius = dp(59) * scale;
        movementX = swapped ? getWidth() - inset - radius : inset + radius;
        movementY = getHeight() - inset - radius;
        actionX = swapped ? inset + radius : getWidth() - inset - radius;
        actionY = getHeight() - inset - radius;
    }

    private void set(int index, boolean down) {
        if (pressed[index] == down) return;
        pressed[index] = down;
        input.key(KEYS[index], down);
        invalidate();
    }

    private void updateMove(float x, float y) {
        float dx = (x - movementX) / radius;
        float dy = (y - movementY) / radius;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < 0.27f) {
            dx = 0f;
            dy = 0f;
        } else if (distance > 1f) {
            dx /= distance;
            dy /= distance;
        }
        // D-pad is a four-way cross; joystick supports diagonals.
        if (padMode == 1) {
            if (Math.abs(dx) >= Math.abs(dy)) dy = 0f; else dx = 0f;
        }
        thumbX = dx;
        thumbY = dy;
        set(0, dx < -0.28f);
        set(1, dx > 0.28f);
        set(2, dy < -0.28f);
        set(3, dy > 0.28f);
        invalidate();
    }

    public void releaseAll() {
        movePointer = actionPointer = -1;
        thumbX = thumbY = 0f;
        for (int k = 0; k < pressed.length; k++) set(k, false);
        invalidate();
    }

    private boolean near(float x, float y, float cx, float cy, float range) {
        return Math.hypot(x - cx, y - cy) <= range;
    }

    private int tone(int alpha, int red, int green, int blue) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), red, green, blue);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        computeLayout();
        int alpha = new int[]{80, 136, 196}[opacityIndex];
        if (padMode == 1) drawDpad(canvas, alpha);
        else drawJoystick(canvas, alpha);
        drawAction(canvas, actionX, actionY, radius * 0.72f, "USE", pressed[4], alpha);
    }

    /** Outer fixed ring and a moving thumbstick, rather than a MOVE text disc. */
    private void drawJoystick(Canvas canvas, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(alpha / 2, 8, 7, 12));
        canvas.drawCircle(movementX, movementY, radius * 1.04f, paint);

        paint.setColor(tone(alpha, 42, 34, 53));
        canvas.drawCircle(movementX, movementY, radius * 0.89f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(tone(alpha + 80, 224, 197, 148));
        canvas.drawCircle(movementX, movementY, radius * 0.89f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(alpha / 2, 151, 127, 154));
        canvas.drawCircle(movementX, movementY, radius * 0.60f, paint);

        float x = movementX + thumbX * radius * 0.52f;
        float y = movementY + thumbY * radius * 0.52f;
        float puck = radius * 0.43f;
        paint.setColor(tone(Math.min(245, alpha + 40), 94, 74, 108));
        canvas.drawCircle(x, y, puck, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setColor(tone(Math.min(255, alpha + 110), 250, 218, 167));
        canvas.drawCircle(x, y, puck, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(alpha + 70, 205, 177, 212));
        canvas.drawCircle(x - puck * 0.23f, y - puck * 0.23f, puck * 0.22f, paint);
    }

    /** A real cross-shaped D-pad with four individually highlighted arms. */
    private void drawDpad(Canvas canvas, int alpha) {
        float w = radius * 0.36f;
        float len = radius * 0.93f;
        canvas.save();
        canvas.translate(movementX, movementY);

        Path cross = new Path();
        cross.moveTo(-w, -len);
        cross.lineTo(w, -len);
        cross.lineTo(w, -w);
        cross.lineTo(len, -w);
        cross.lineTo(len, w);
        cross.lineTo(w, w);
        cross.lineTo(w, len);
        cross.lineTo(-w, len);
        cross.lineTo(-w, w);
        cross.lineTo(-len, w);
        cross.lineTo(-len, -w);
        cross.lineTo(-w, -w);
        cross.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(alpha, 47, 37, 59));
        canvas.drawPath(cross, paint);
        float edge = dp(3);
        paint.setColor(tone(alpha + 55, 17, 14, 23));
        canvas.drawRoundRect(-w + edge, -len + edge, w - edge, -w, dp(5), dp(5), paint);
        canvas.drawRoundRect(-w + edge, w, w - edge, len - edge, dp(5), dp(5), paint);
        canvas.drawRoundRect(-len + edge, -w + edge, -w, w - edge, dp(5), dp(5), paint);
        canvas.drawRoundRect(w, -w + edge, len - edge, w - edge, dp(5), dp(5), paint);

        paint.setColor(tone(alpha + 70, 178, 103, 60));
        if (pressed[2]) canvas.drawRoundRect(-w + edge, -len + edge, w - edge, -w, dp(5), dp(5), paint);
        if (pressed[3]) canvas.drawRoundRect(-w + edge, w, w - edge, len - edge, dp(5), dp(5), paint);
        if (pressed[0]) canvas.drawRoundRect(-len + edge, -w + edge, -w, w - edge, dp(5), dp(5), paint);
        if (pressed[1]) canvas.drawRoundRect(w, -w + edge, len - edge, w - edge, dp(5), dp(5), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(tone(alpha + 85, 224, 197, 148));
        canvas.drawPath(cross, paint);

        paint.setStyle(Paint.Style.FILL);
        drawArrow(canvas, 0f, -radius * 0.66f, 0f, alpha);
        drawArrow(canvas, radius * 0.66f, 0f, 90f, alpha);
        drawArrow(canvas, 0f, radius * 0.66f, 180f, alpha);
        drawArrow(canvas, -radius * 0.66f, 0f, 270f, alpha);

        paint.setColor(tone(alpha + 45, 25, 20, 29));
        canvas.drawCircle(0, 0, radius * 0.20f, paint);
        canvas.restore();
    }

    private void drawArrow(Canvas canvas, float x, float y, float degrees, int alpha) {
        float a = radius * 0.14f;
        Path arrow = new Path();
        arrow.moveTo(0f, -a);
        arrow.lineTo(a, a * 0.70f);
        arrow.lineTo(-a, a * 0.70f);
        arrow.close();
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(degrees);
        paint.setColor(tone(alpha + 95, 240, 220, 178));
        canvas.drawPath(arrow, paint);
        canvas.restore();
    }

    private void drawAction(Canvas canvas, float x, float y, float r, String label,
                            boolean down, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(down ? tone(225, 143, 82, 51) : tone(alpha, 52, 41, 65));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(tone(alpha + 60, 224, 197, 148));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(r * 0.53f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(label, x, y - (fm.ascent + fm.descent) / 2f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        computeLayout();
        int action = event.getActionMasked();
        int changed = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(changed), y = event.getY(changed);
            int pointer = event.getPointerId(changed);
            if (movePointer == -1 && near(x, y, movementX, movementY, radius * 1.27f)) {
                movePointer = pointer;
                updateMove(x, y);
                return true;
            }
            if (actionPointer == -1 && near(x, y, actionX, actionY, radius * 0.75f)) {
                actionPointer = pointer;
                set(4, true);
                return true;
            }
            // A primary tap outside the on-screen controls must reach the WebView.
            // Otherwise the Flash title-screen Play button cannot be selected
            // while virtual controls are visible. Keep secondary touches in a
            // control-owned stream here so held movement/action stays intact.
            return action != MotionEvent.ACTION_DOWN;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (movePointer != -1) {
                int p = event.findPointerIndex(movePointer);
                if (p >= 0) updateMove(event.getX(p), event.getY(p));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int pointer = event.getPointerId(changed);
            if (pointer == movePointer) {
                movePointer = -1;
                thumbX = thumbY = 0f;
                for (int j = 0; j < 4; j++) set(j, false);
                invalidate();
            }
            if (pointer == actionPointer) { actionPointer = -1; set(4, false); }
            return true;
        }
        return true;
    }
}
