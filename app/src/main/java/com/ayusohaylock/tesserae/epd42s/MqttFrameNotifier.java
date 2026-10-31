package com.ayusohaylock.tesserae.epd42s;

import android.util.Log;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/** MQTT is a notification channel only. All frame bytes still come from authenticated REST. */
final class MqttFrameNotifier {
    interface Listener { void frameAvailable(); }
    private static final String TAG="TesseraeMQTT";
    private MqttAsyncClient client;
    synchronized void start(String broker,String id,Listener listener) throws Exception {
        stop();
        if(!broker.startsWith("tcp://") && !broker.startsWith("ssl://"))
            throw new IllegalArgumentException("Use tcp:// or ssl:// for MQTT broker");
        if(!id.matches("[a-z][a-z0-9_-]{1,31}")) throw new IllegalArgumentException("Invalid device id");
        final String topic="tesserae/"+id+"/frame/png";
        Log.i(TAG,"Connecting to broker "+broker+" (unauthenticated)");
        client=new MqttAsyncClient(broker,"tesserae-android-"+id,new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect,String serverURI) {
                try {
                    MqttAsyncClient c=client;
                    if(c!=null && c.isConnected()) c.subscribe(topic,1,null,new IMqttActionListener(){
                        @Override public void onSuccess(IMqttToken token){Log.i(TAG,"Subscribed to "+topic+" QoS 1");}
                        @Override public void onFailure(IMqttToken token,Throwable e){Log.w(TAG,"MQTT subscription rejected; REST remains active",e);}
                    });
                } catch(Exception e) {Log.w(TAG,"Subscription failed",e);}
            }
            @Override public void connectionLost(Throwable cause) {Log.w(TAG,"MQTT connection lost; REST polling remains active");}
            @Override public void messageArrived(String received,MqttMessage message) {
                if(!topic.equals(received))return;
                // Do not trust arbitrary MQTT URLs or payload commands. REST authenticates and validates the frame.
                Log.i(TAG,"Frame notification received");
                listener.frameAvailable();
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) {}
        });
        MqttConnectOptions options=new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        client.connect(options,null,new IMqttActionListener(){
            @Override public void onSuccess(IMqttToken t){Log.i(TAG,"MQTT connected");}
            @Override public void onFailure(IMqttToken t,Throwable e){Log.w(TAG,"MQTT connection failed; REST polling remains active",e);}
        });
    }
    synchronized void stop() {
        MqttAsyncClient old=client;client=null;
        if(old!=null) {
            try { old.disconnectForcibly(200,200); } catch(Exception ignored){}
            try { old.close(); } catch(Exception ignored){}
        }
    }
}
