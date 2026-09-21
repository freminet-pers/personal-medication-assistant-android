package com.lunamax.medassistant;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Token-backed palette mirroring design/tokens.json. */
final class Palette {
    final int app;
    final int surface;
    final int soft;
    final int primary;
    final int primarySoft;
    final int text;
    final int secondary;
    final int border;
    final int warning;
    final int warningSoft;
    final int danger;
    final int dangerSoft;
    final int white;

    private Palette(boolean dark) {
        if (dark) {
            app = Color.rgb(16, 32, 29);
            surface = Color.rgb(23, 42, 38);
            soft = Color.rgb(32, 58, 52);
            primary = Color.rgb(120, 213, 196);
            primarySoft = Color.rgb(36, 81, 72);
            text = Color.rgb(243, 248, 243);
            secondary = Color.rgb(177, 198, 188);
            border = Color.rgb(54, 83, 75);
            warning = Color.rgb(255, 211, 123);
            warningSoft = Color.rgb(90, 66, 22);
            danger = Color.rgb(255, 180, 171);
            dangerSoft = Color.rgb(90, 37, 38);
            white = Color.rgb(23, 42, 38);
        } else {
            app = Color.rgb(247, 248, 244);
            surface = Color.WHITE;
            soft = Color.rgb(238, 242, 235);
            primary = Color.rgb(15, 102, 93);
            primarySoft = Color.rgb(214, 239, 232);
            text = Color.rgb(20, 37, 34);
            secondary = Color.rgb(101, 122, 115);
            border = Color.rgb(215, 225, 219);
            warning = Color.rgb(138, 82, 0);
            warningSoft = Color.rgb(255, 240, 199);
            danger = Color.rgb(163, 60, 60);
            dangerSoft = Color.rgb(252, 227, 225);
            white = Color.WHITE;
        }
    }

    static Palette from(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new Palette(dark);
    }
}
