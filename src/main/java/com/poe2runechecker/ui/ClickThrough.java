package com.poe2runechecker.ui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.awt.Window;

/** Делает окно «сквозным» для мыши (WS_EX_TRANSPARENT) — клики проходят в игру. */
final class ClickThrough {

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);
        // на x64 экспортируются только *Ptr W/A варианты
        long GetWindowLongPtrW(Pointer hWnd, int nIndex);
        long SetWindowLongPtrW(Pointer hWnd, int nIndex, long dwNewLong);
    }

    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_LAYERED = 0x00080000;
    private static final int WS_EX_TRANSPARENT = 0x00000020;

    static void apply(Window w) {
        try {
            Pointer hwnd = Native.getWindowPointer(w);
            long ex = User32.INSTANCE.GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
            User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_EXSTYLE,
                    ex | WS_EX_LAYERED | WS_EX_TRANSPARENT);
        } catch (Throwable t) {
            System.err.println("[clickthrough] " + t.getMessage());
        }
    }

    private ClickThrough() {}
}
