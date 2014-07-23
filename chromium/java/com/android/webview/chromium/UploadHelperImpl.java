/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.webview.chromium;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.WebChromeClient;

import org.chromium.android_webview.AwContentsClient;

import java.io.File;

public class UploadHelperImpl extends WebChromeClient.UploadHelper {

    private final static String IMAGE_MIME_TYPE = "image/*";
    private final static String VIDEO_MIME_TYPE = "video/*";
    private final static String AUDIO_MIME_TYPE = "audio/*";

    private AwContentsClient.FileChooserParams mParams;
    private Context mContext;
    private String mCameraFilePath;

    public UploadHelperImpl(AwContentsClient.FileChooserParams params, Context context) {
        mParams = params;
        mContext = context;
    }

    @Override
    public Intent buildIntent() {
      // TODO(sgurun) Move this code to Aw. Once code is moved
      // and merged to M37 get rid of this.
      String mimeType = "*/*";
      if (mParams.acceptTypes != null) {
          mimeType = mParams.acceptTypes.split(";")[0];
      }
      boolean capture = mParams.capture;
      if (mimeType.equals(IMAGE_MIME_TYPE)) {
          if (capture) {
              // Specified 'image/*' and requested capture. Launch the camera.
              return createCameraIntent();
          } else {
              // Specified just 'image/*', and no capture. Show a traditional picker filtered
              // on accept type by sending an intent for both the Camera and image/* OPENABLE.
              Intent chooser = createChooserIntent(createCameraIntent());
              chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(IMAGE_MIME_TYPE));
              return chooser;
          }
      } else if (mimeType.equals(VIDEO_MIME_TYPE)) {
          if (capture) {
              // Specified 'video/*' and requested capture. Launch the camcorder.
              return createCamcorderIntent();
          } else {
              // Specified just 'video/*', and no capture. Show a traditional file picker,
              // filtered on accept type by sending an intent for both camcorder
              // and video/* OPENABLE.
              Intent chooser = createChooserIntent(createCamcorderIntent());
              chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(VIDEO_MIME_TYPE));
              return chooser;
          }
      } else if (mimeType.equals(AUDIO_MIME_TYPE)) {
          if (capture) {
              // Specified 'audio/*' and requested capture. Launch the sound recorder.
              return createSoundRecorderIntent();
          } else {
              // Specified just 'audio/*', and no capture. Show a traditional file picker,
              // filtered on accept type by sending an intent for both the sound
              // recorder and audio/* OPENABLE.
              Intent chooser = createChooserIntent(createSoundRecorderIntent());
              chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(AUDIO_MIME_TYPE));
              return chooser;
          }
      }
      return createDefaultOpenableIntent();
  }

  @Override
  public Uri[] parseResult(int resultCode, Intent intent) {
      if (resultCode == Activity.RESULT_CANCELED) {
          return null;
      }
      Uri result = intent == null || resultCode != Activity.RESULT_OK ? null
              : intent.getData();

      // As we ask the camera to save the result of the user taking
      // a picture, the camera application does not return anything other
      // than RESULT_OK. So we need to check whether the file we expected
      // was written to disk in the in the case that we
      // did not get an intent returned but did get a RESULT_OK. If it was,
      // we assume that this result has came back from the camera.
      if (result == null && intent == null && resultCode == Activity.RESULT_OK
              && mCameraFilePath != null) {
          File cameraFile = new File(mCameraFilePath);
          if (cameraFile.exists()) {
              result = Uri.fromFile(cameraFile);
              // Broadcast to the media scanner that we have a new photo
              // so it will be added into the gallery for the user.
              mContext.sendBroadcast(
                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
          }
      }

      Uri[] uris = null;
      if (result != null) {
          uris = new Uri[1];
          uris[0] = result;
      }
      return uris;
  }

  private Intent createDefaultOpenableIntent() {
      // Create and return a chooser with the default OPENABLE
      // actions including the camera, camcorder and sound
      // recorder where available.
      Intent i = new Intent(Intent.ACTION_GET_CONTENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("*/*");

      Intent chooser = createChooserIntent(createCameraIntent(), createCamcorderIntent(),
          createSoundRecorderIntent());
      chooser.putExtra(Intent.EXTRA_INTENT, i);
      return chooser;
  }

  private Intent createChooserIntent(Intent... intents) {
      Intent chooser = new Intent(Intent.ACTION_CHOOSER);
      chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents);
      return chooser;
  }

  private Intent createOpenableIntent(String type) {
      Intent i = new Intent(Intent.ACTION_GET_CONTENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType(type);
      return i;
  }

  private Intent createCameraIntent() {
      Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      File cameraDataDir = Environment.getExternalStoragePublicDirectory(
              Environment.DIRECTORY_DCIM);
      mCameraFilePath = cameraDataDir.getAbsolutePath() + File.separator +
                System.currentTimeMillis() + ".jpg";
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mCameraFilePath)));
      return intent;
  }

  private Intent createCamcorderIntent() {
      return new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
  }

  private Intent createSoundRecorderIntent() {
      return new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
  }
}
