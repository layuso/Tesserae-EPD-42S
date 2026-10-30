package com.ayusohaylock.tesserae.epd42s;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.NetworkInterface;
import java.util.Collections;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Conservative software-rendered UI; intentionally never requests OS orientation. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final NativeTesseraeClient api=new NativeTesseraeClient();
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView message;
    private ImageView frame;
    private String etag=null;
    private volatile boolean fetching=false;
    private boolean active=false;
    private final Runnable poll=new Runnable(){public void run(){fetchFrame();}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("device",MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        showHome();
    }
    private String value(String k){return prefs.getString(k,"");}
    private TextView text(String value,int sp){
        TextView t=new TextView(this); t.setText(value);t.setTextColor(Color.BLACK);t.setTextSize(sp);t.setPadding(18,14,18,14);return t;
    }
    private Button button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());return b;
    }
    private void layout(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);root.setBackgroundColor(Color.WHITE);setContentView(root);
    }
    private void notice(String s){runOnUiThread(()->{if(message!=null)message.setText(s);});}
    private void showHome(){
        if(value("token").isEmpty() || value("device_id").isEmpty()){showSetup();return;}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);setContentView(root);message=null;
        frame=new ImageView(this);frame.setBackgroundColor(Color.WHITE);frame.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(frame,new LinearLayout.LayoutParams(-1,-1));
        frame.setOnLongClickListener(v -> { showSettings(); return true; });
        loadCachedFrame();
        schedule(0);
    }
    private File frameCache(){return new File(getFilesDir(),"last-frame.png");}
    private void loadCachedFrame(){try(FileInputStream in=new FileInputStream(frameCache())){Bitmap bmp=BitmapFactory.decodeStream(in);if(bmp!=null)frame.setImageBitmap(bmp);}catch(Exception ignored){}}
    private void cacheFrame(byte[] data){File tmp=new File(getFilesDir(),"last-frame.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.flush();out.getFD().sync();if(!tmp.renameTo(frameCache()))throw new Exception("cache rename");}catch(Exception e){android.util.Log.w("TesseraeEPD","Cache failure",e);tmp.delete();}}
    private void showSettings(){
        new AlertDialog.Builder(this).setTitle("Tesserae EPD-42S")
            .setItems(new String[]{"Refresh now","Connection details","Reconfigure / re-pair"},(d,n)->{
                if(n==0) fetchFrame();
                if(n==1) new AlertDialog.Builder(this).setMessage("Server: "+value("base")+"\nDevice: "+value("device_id")).setPositiveButton("OK",null).show();
                if(n==2) new AlertDialog.Builder(this).setMessage("Clear local token and reconnect? The existing server-side device remains registered.").setPositiveButton("Reset",(a,b)->{
                    prefs.edit().remove("token").remove("device_id").apply();etag=null;showSetup();
                }).setNegativeButton("Cancel",null).show();
            }).show();
    }
    private void showSetup(){
        ui.removeCallbacks(poll);layout();root.addView(text("Connect to Tesserae",30));
        EditText address=new EditText(this);address.setSingleLine(true);address.setHint("https://eink.example.com");address.setText(value("base"));root.addView(address);
        root.addView(text("Choose how to register this display. The same hardware identity is used in either method.",18));
        root.addView(button("Find display in Tesserae (admin approval)",()->enroll(address.getText().toString(),null)));
        root.addView(button("Enter six-digit pairing code",()->{
            EditText entry=new EditText(this);entry.setSingleLine(true);entry.setHint("Pairing code");entry.setInputType(2);
            new AlertDialog.Builder(this).setTitle("Pairing code").setView(entry).setNegativeButton("Cancel",null)
                .setPositiveButton("Pair",(d,w)->enroll(address.getText().toString(),entry.getText().toString().trim())).show();
        }));
        message=text("Android stays in its original landscape mode. Configure panel rotation on Tesserae's device card after registration.",18);root.addView(message);
    }
    private String mac(){
        try {
            NetworkInterface nic=NetworkInterface.getByName("eth0");
            if(nic==null)throw new Exception("No eth0");
            byte[] bytes=nic.getHardwareAddress();
            if(bytes==null || bytes.length!=6)throw new Exception("MAC missing");
            StringBuilder s=new StringBuilder();
            for(byte b:bytes){if(s.length()>0)s.append(":");s.append(String.format("%02X",b&255));}
            String mac=s.toString();
            if(mac.equals("00:00:00:00:00:00"))throw new Exception("Invalid MAC");
            return mac;
        }catch(Exception e){throw new IllegalStateException("Ethernet MAC unavailable: "+e.getMessage());}
    }
    private JSONObject identity() throws Exception {
        JSONObject j=new JSONObject();
        String mac=mac();
        j.put("device_id","avalue_"+mac.replace(":","").toLowerCase());
        j.put("kind","circuitpython_generic"); // Provisional kind: verify renderer with Tesserae.
        j.put("name","Avalue EPD-42S");
        j.put("panel_w",2880); j.put("panel_h",2160);
        j.put("rotation",0); // Initial registration baseline; Tesserae owns device orientation.
        j.put("gamut","gray_16");j.put("format","png");
        j.put("fw_version","0.1.0");j.put("mac",mac);
        return j;
    }
    private void enroll(String server,String code){
        final String base;
        try{base=NativeTesseraeClient.normalizeBase(server);}catch(Exception ex){notice(ex.getMessage());return;}
        prefs.edit().putString("base",base).apply();
        notice(code==null?"Announcing device. Approve it in Tesserae → Settings → Devices.":"Submitting pairing code...");
        worker.execute(()->{
            try{
                NativeTesseraeClient.Reply r=code==null?api.discover(base,identity()):api.pair(base,code,identity());
                if(r.status>=400)throw new Exception("Registration HTTP "+r.status+": "+r.error);
                JSONObject j=r.data;
                String token=j.optString("device_token","");
                if(!token.isEmpty()){
                    String id=j.optString("device_id",identity().getString("device_id"));
                    prefs.edit().putString("token",token).putString("device_id",id).apply();
                    runOnUiThread(this::showHome);
                }else if(code==null && j.optBoolean("discovered",false)){
                    int delay=Math.max(15,Math.min(300,j.optInt("retry_after_s",30)));
                    notice("Waiting for administrator approval. Retry in "+delay+" seconds.");
                    ui.postDelayed(()->enroll(base,null),delay*1000L);
                } else notice("No device token returned; check registration in Tesserae.");
            }catch(Exception ex){notice("Registration failed: "+ex.getMessage());}
        });
    }
    private void registrationRevoked(String expectedToken,int status){
        android.util.Log.w("TesseraeEPD","Registration invalid (HTTP "+status+"); returning to setup");
        runOnUiThread(()->{
            if(!expectedToken.equals(value("token")))return;
            ui.removeCallbacks(poll);
            prefs.edit().remove("token").remove("device_id").apply();
            etag=null;
            showSetup();
            notice("Device registration was removed or rejected (HTTP "+status+"). Reconnect using admin approval or a pairing code.");
        });
    }
    private void schedule(int seconds){
        ui.removeCallbacks(poll);
        ui.postDelayed(poll,Math.max(0,seconds)*1000L);
    }
    private void fetchFrame(){
        if(fetching || value("token").isEmpty())return;
        fetching=true;
        final String base=value("base"),id=value("device_id"),token=value("token");
        worker.execute(()->{
            try{
                NativeTesseraeClient.Reply r=api.frame(base,id,token,frame!=null && frame.getDrawable()!=null?etag:null);
                if(r.status==401 || r.status==403 || r.status==404){registrationRevoked(token,r.status);return;}
                if(r.status==304){schedule(30);return;}
                if(r.status==204){schedule(30);return;}
                if(r.status!=200)throw new Exception("Frame HTTP "+r.status+": "+r.error);
                if(!"png".equalsIgnoreCase(r.data.optString("format")) && !r.data.optString("url","").endsWith(".png"))throw new Exception("Server did not offer a PNG renderer");
                byte[] bytes=api.downloadPng(base,r.data.getString("url"));
                Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bmp==null)throw new Exception("PNG decode failed");
                if(bmp.getWidth()!=2880 || bmp.getHeight()!=2160)throw new Exception("Unexpected frame "+bmp.getWidth()+"x"+bmp.getHeight());
                if(!token.equals(value("token")))return;
                cacheFrame(bytes);
                String tag=r.etag!=null?r.etag:r.data.optString("render_id","");
                runOnUiThread(()->{
                    if(token.equals(value("token")) && frame!=null){frame.setImageBitmap(bmp);etag=tag;}
                });
                schedule(30);
            }catch(Exception ex){android.util.Log.w("TesseraeEPD","Frame refresh failed: "+ex.getMessage());if(token.equals(value("token")))schedule(45);}
            finally{fetching=false;}
        });
    }
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
