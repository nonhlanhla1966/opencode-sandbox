package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.media.MediaRecorder;
import android.net.Uri;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.io.File;

/** Voice note capture via MediaRecorder (AAC in MP4 container). */
public final class VoiceRecorder {

    private MediaRecorder recorder;
    private File output;
    private long startedAt;
    private boolean recording;

    public boolean recording() {
        return recording;
    }

    public boolean start(File outputFile) {
        try {
            MediaRecorder r = new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
            r.setOutputFile(outputFile.getAbsolutePath());
            r.prepare();
            r.start();
            recorder = r;
            output = outputFile;
            startedAt = System.currentTimeMillis();
            recording = true;
            return true;
        } catch (Exception e) {
            release();
            return false;
        }
    }

    /** Stop and return the recorded voice-note attachment (or null). */
    public Attachment stopAndAttachment() {
        if (!recording || output == null) {
            release();
            return null;
        }
        long durationMs = Math.max(0, System.currentTimeMillis() - startedAt);
        release();
        return Attachment.create("voice-" + output.getName(),
                Message.Kind.VOICE_NOTE, Uri.fromFile(output).toString(), "audio/mp4",
                output.length(), 0, 0, durationMs);
    }

    public void cancel() {
        release();
    }

    private void release() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // short recordings may fail to finalize; release anyway
            }
            recorder.release();
            recorder = null;
        }
        recording = false;
    }
}