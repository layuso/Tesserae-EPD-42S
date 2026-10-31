package com.ayusohaylock.tesserae.epd42s;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/** Start the dedicated display after Android finishes booting, but only if enrolled. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p=context.getSharedPreferences("device",Context.MODE_PRIVATE);
        if(p.getString("token","").isEmpty() || p.getString("device_id","").isEmpty())return;
        try {
            Intent start=new Intent(context,MainActivity.class);
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(start);
            Log.i("TesseraeEPD","Boot restore requested");
        } catch(Exception ex){Log.w("TesseraeEPD","Boot restore failed",ex);}
    }
}
