package io.github.v7lin.facebook_applinks;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.applinks.AppLinkData;

import java.util.HashMap;
import java.util.Map;

import bolts.AppLinks;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/** FacebookApplinksPlugin */
public class FacebookApplinksPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.NewIntentListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private FlutterPluginBinding flutterPluginBinding;
  private Handler mainHandler;
  private ActivityPluginBinding activityPluginBinding;

  // --- FlutterPlugin

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    channel = new MethodChannel(binding.getBinaryMessenger(), "v7lin.github.io/facebook_applinks");
    channel.setMethodCallHandler(this);
    flutterPluginBinding = binding;
    mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    channel = null;
    flutterPluginBinding = null;
    mainHandler.removeCallbacksAndMessages(null);
    mainHandler = null;
  }

  // --- ActivityAware

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activityPluginBinding = binding;
    activityPluginBinding.addOnNewIntentListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    activityPluginBinding.removeOnNewIntentListener(this);
    activityPluginBinding = null;
  }

  // --- MethodCallHandler

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
    if (call.method.equals("getInitialAppLink")) {
      Intent intent = activityPluginBinding.getActivity().getIntent();

      Uri targetUrl = null;
      ResolveInfo resolveInfo = flutterPluginBinding.getApplicationContext().getPackageManager().resolveActivity(intent, 0);
      if (resolveInfo != null && resolveInfo.filter.hasCategory("facebook_applinks")) {
        targetUrl = AppLinks.getTargetUrlFromInboundIntent(flutterPluginBinding.getApplicationContext(), intent);
      }
      result.success(targetUrl != null ? targetUrl.toString() : null);
    } else if (call.method.equals("fetchDeferredAppLink")) {
      AppLinkData.fetchDeferredAppLinkData(flutterPluginBinding.getApplicationContext(), new AppLinkData.CompletionHandler() {
        @Override
        public void onDeferredAppLinkDataFetched(@Nullable final AppLinkData appLinkData) {
          if (appLinkData != null) {
            if (mainHandler != null) {
              mainHandler.post(new Runnable() {
                @Override
                public void run() {
                  if (result != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("target_url", appLinkData.getTargetUri() != null ? appLinkData.getTargetUri().toString() : null);
                    map.put("promo_code", appLinkData.getPromotionCode());
                    result.success(map);
                  }
                }
              });
            }
          } else {
            if (mainHandler != null) {
              mainHandler.post(new Runnable() {
                @Override
                public void run() {
                  if (result != null) {
                    result.error("FAILED", "AppLink is null", null);
                  }
                }
              });
            }
          }
        }
      });
    } else {
      result.notImplemented();
    }
  }

  // --- NewIntentListener

  @Override
  public boolean onNewIntent(Intent intent) {
    ResolveInfo resolveInfo = flutterPluginBinding.getApplicationContext().getPackageManager().resolveActivity(intent, 0);
    if (resolveInfo != null && resolveInfo.filter.hasCategory("facebook_applinks")) {
      Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(flutterPluginBinding.getApplicationContext(), intent);
      if (channel != null) {
        channel.invokeMethod("handleAppLink", targetUrl != null ? targetUrl.toString() : null);
      }
      return true;
    }
    return false;
  }
}
