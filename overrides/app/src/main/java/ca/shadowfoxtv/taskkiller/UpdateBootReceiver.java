package ca.shadowfoxtv.taskkiller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UpdateScheduler.schedule(context);
        UpdateScheduler.checkNow(context);
    }
}