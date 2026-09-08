package ca.shadowfoxtv.taskkiller;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

/**
 * TV/Fire TV friendly button that opens the existing ShadowFox in-app updater.
 * Kept as a self-contained view so the v5 dashboard and updater remain loosely coupled.
 */
public class UpdateCenterButton extends Button {
    public UpdateCenterButton(Context context) {
        super(context);
        init();
    }

    public UpdateCenterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UpdateCenterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(false);
        setOnClickListener(v -> openUpdateCenter());
        setOnFocusChangeListener((View v, boolean focused) -> {
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(100).start();
            v.setElevation(focused ? 18f : 2f);
        });
    }

    private void openUpdateCenter() {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra("shadowfox_update_center", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getContext().startActivity(intent);
    }
}
