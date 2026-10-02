package com.didi.diary;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

@CapacitorPlugin(name = "MlKitOcr")
public class MlKitOcrPlugin extends Plugin {

    // Every recognized word with its box (pixels of the image that was sent) and its line number
    private JSArray toArr(Text t) {
        JSArray arr = new JSArray();
        int li = 0;
        for (Text.TextBlock b : t.getTextBlocks()) {
            for (Text.Line l : b.getLines()) {
                for (Text.Element e : l.getElements()) {
                    Rect r = e.getBoundingBox();
                    if (r == null) continue;
                    JSObject o = new JSObject();
                    o.put("t", e.getText());
                    o.put("l", r.left);
                    o.put("tp", r.top);
                    o.put("r", r.right);
                    o.put("b", r.bottom);
                    o.put("ln", li);
                    arr.put(o);
                }
                li++;
            }
        }
        return arr;
    }

    @PluginMethod
    public void recognize(final PluginCall call) {
        try {
            String b64 = call.getString("image");
            if (b64 == null) { call.reject("No image"); return; }
            String script = call.getString("script", "both");
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) { call.reject("Bad image"); return; }
            final InputImage img = InputImage.fromBitmap(bmp, 0);

            if ("latin".equals(script)) {
                final TextRecognizer lat = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                lat.process(img)
                    .addOnSuccessListener(t -> {
                        JSObject o = new JSObject();
                        o.put("latin", t.getText());
                        o.put("lEl", toArr(t));
                        lat.close();
                        call.resolve(o);
                    })
                    .addOnFailureListener(e -> { lat.close(); call.reject(String.valueOf(e.getMessage())); });
                return;
            }

            final TextRecognizer dev = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
            final boolean both = "both".equals(script);
            dev.process(img)
                .addOnSuccessListener(td -> {
                    final String dt = td.getText();
                    final JSArray de = toArr(td);
                    dev.close();
                    if (!both) {
                        JSObject o = new JSObject();
                        o.put("devanagari", dt);
                        o.put("dEl", de);
                        call.resolve(o);
                        return;
                    }
                    final TextRecognizer lat = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    lat.process(img)
                        .addOnSuccessListener(tl -> {
                            JSObject o = new JSObject();
                            o.put("devanagari", dt);
                            o.put("dEl", de);
                            o.put("latin", tl.getText());
                            o.put("lEl", toArr(tl));
                            lat.close();
                            call.resolve(o);
                        })
                        .addOnFailureListener(e -> {
                            JSObject o = new JSObject();
                            o.put("devanagari", dt);
                            o.put("dEl", de);
                            lat.close();
                            call.resolve(o);
                        });
                })
                .addOnFailureListener(e -> { dev.close(); call.reject(String.valueOf(e.getMessage())); });
        } catch (Throwable e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
                          }
