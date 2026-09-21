package com.lunamax.medassistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small, dependency-free UI primitives shared by the single-activity surface. */
final class UiKit {
    static final long PRESS_MS = 140L;
    static final long CONTENT_MS = 220L;
    static final long LIST_STAGGER_MS = 28L;
    static final long MAX_STAGGER_DELAY_MS = 140L;

    private UiKit() { }

    static boolean animationsEnabled(Context context) {
        if (!ValueAnimator.areAnimatorsEnabled()) return false;
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    static void enter(Context context, View view, int index) {
        if (!animationsEnabled(context)) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(dp(context, 8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(MAX_STAGGER_DELAY_MS, Math.max(0, index) * LIST_STAGGER_MS))
                .setDuration(CONTENT_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    static void press(View view) {
        view.setOnTouchListener((v, event) -> {
            if (!animationsEnabled(v.getContext())) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(PRESS_MS).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS).start();
            }
            return false;
        });
    }

    static void reset(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
