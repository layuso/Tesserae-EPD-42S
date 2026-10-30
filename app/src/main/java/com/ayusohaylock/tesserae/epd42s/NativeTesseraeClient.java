package com.ayusohaylock.tesserae.epd42s;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Native Tesserae protocol. Blocking methods: call on worker thread only. */
public final class NativeTesseraeClient {
    public static final class Reply {
        public final int status;
        public final JSONObject data;
        public final String etag;
        public final String error;
        Reply(int status, JSONObject data, String etag, String error) {
            this.status=status; this.data=data; this.etag=etag; this.error=error;
        }
    }
    private static String safeBase(String base) {
        if (base == null) throw new IllegalArgumentException("Server URL is required");
        String b=base.trim();
        if (!b.startsWith("https://")) throw new IllegalArgumentException("HTTPS server URL required");
        while(b.endsWith("/")) b=b.substring(0,b.length()-1);
        try { URL u=new URL(b); if(u.getHost().isEmpty() || u.getUserInfo()!=null || u.getQuery()!=null || u.getRef()!=null || !u.getPath().isEmpty()) throw new Exception(); }
        catch(Exception e){throw new IllegalArgumentException("Enter just the HTTPS server origin");}
        return b;
    }
    public static String normalizeBase(String base) { return safeBase(base); }
    private static HttpURLConnection open(URL url, String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept","application/json");
        return c;
    }
    private static byte[] readBytes(InputStream stream, int max) throws Exception {
        try (InputStream in=stream; ByteArrayOutputStream buf=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[16384]; int n;
            while((n=in.read(bytes))!=-1) { if(buf.size()+n>max) throw new Exception("Response too large"); buf.write(bytes,0,n); }
            return buf.toByteArray();
        }
    }
    private static Reply request(String base,String path,String token,String pairing,JSONObject body,String etag) throws Exception {
        URL u=new URL(safeBase(base)+path);
        HttpURLConnection c=open(u,body==null?"GET":"POST");
        try {
            if(token!=null && !token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
            if(pairing!=null && !pairing.isEmpty()) c.setRequestProperty("X-Pairing-Code",pairing);
            if(etag!=null && !etag.isEmpty()) c.setRequestProperty("If-None-Match",etag);
            if(body!=null){
                c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
                byte[] payload=body.toString().getBytes("UTF-8");
                try(OutputStream out=c.getOutputStream()){out.write(payload);}
            }
            int status=c.getResponseCode();
            String nextTag=c.getHeaderField("ETag");
            if(status==204 || status==304) return new Reply(status,null,nextTag,null);
            InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            byte[] raw=stream==null?new byte[0]:readBytes(stream,512*1024);
            JSONObject parsed=raw.length==0?new JSONObject():new JSONObject(new String(raw,"UTF-8"));
            String error=status>=400?parsed.optString("error",parsed.optString("message","HTTP "+status)):null;
            return new Reply(status,parsed,nextTag,error);
        } finally {c.disconnect();}
    }
    public Reply discover(String base,JSONObject identity) throws Exception {return request(base,"/api/v1/device/discover",null,null,identity,null);}
    public Reply pair(String base,String code,JSONObject identity) throws Exception {
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Six-digit pairing code required");
        return request(base,"/api/v1/device/register",null,code,identity,null);
    }
    public Reply frame(String base,String id,String token,String etag) throws Exception {
        if(!id.matches("[a-z][a-z0-9_-]{1,31}"))throw new IllegalArgumentException("Invalid device id");
        return request(base,"/api/v1/device/"+id+"/frame",token,null,null,etag);
    }
    public byte[] downloadPng(String base,String rawUrl) throws Exception {
        URL origin=new URL(safeBase(base));
        URL image=new URL(rawUrl);
        // Some reverse proxies generate HTTP URLs despite an HTTPS public origin.
        // Upgrade only same-host URLs, never follow arbitrary redirects.
        if(!origin.getHost().equalsIgnoreCase(image.getHost()) || origin.getPort()!=image.getPort())
            throw new SecurityException("Frame URL origin mismatch");
        if(!image.getProtocol().equals("https") && !image.getProtocol().equals("http"))
            throw new SecurityException("Unsupported frame scheme");
        if(!image.getPath().matches("/renders/[a-zA-Z0-9._-]+\\.png"))
            throw new SecurityException("Unexpected frame path");
        URL https=new URL("https",origin.getHost(),origin.getPort(),image.getFile());
        HttpURLConnection c=open(https,"GET");
        try {
            int code=c.getResponseCode();
            if(code!=200)throw new Exception("Frame download HTTP "+code);
            String type=c.getContentType();
            if(type==null || !type.toLowerCase(Locale.US).startsWith("image/png")) throw new Exception("Expected PNG, got "+type);
            return readBytes(c.getInputStream(),24*1024*1024);
        } finally {c.disconnect();}
    }
}
