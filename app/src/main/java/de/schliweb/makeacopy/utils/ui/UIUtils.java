/*
 * Copyright 2026 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import de.schliweb.makeacopy.R;
import java.lang.ref.WeakReference;
import lombok.experimental.UtilityClass;

/**
 * A utility class containing helper methods for common user interface tasks. This class provides
 * functions to pad bottom bars for system insets, handle status bar height, and display Toast
 * messages safely.
 *
 * <p>This class is not intended to be instantiated.
 */
@UtilityClass
public class UIUtils {
  private static final String TAG = "UIUtils";

  /** On-screen time of a message, matching the system values for a short and a long toast. */
  private static final int MESSAGE_SHORT_MS = 2000;

  private static final int MESSAGE_LONG_MS = 3500;
  private static final int MESSAGE_MAX_LINES = 5;
  private static final int MESSAGE_RETRY_DELAY_MS = 500;

  /** Limits for a row of controls that counts as stacked on the bottom bar, in dp. */
  private static final int ACTION_ROW_MAX_GAP_DP = 24;

  private static final int ACTION_ROW_MAX_HEIGHT_DP = 96;

  private static WeakReference<Activity> foregroundActivity = new WeakReference<>(null);

  /**
   * Enables or greys out a control and, for a group such as a RadioGroup, its direct children.
   * Disabled controls keep their state so that re-enabling restores the previous choice.
   */
  public static void setEnabledWithAlpha(View v, boolean enabled) {
    if (v == null) return;
    v.setEnabled(enabled);
    v.setAlpha(enabled ? 1f : 0.4f);
    if (v instanceof ViewGroup vg) {
      for (int i = 0; i < vg.getChildCount(); i++) vg.getChildAt(i).setEnabled(enabled);
    }
  }

  /**
   * Pads the content of a bottom bar by the system insets (navigation bar, display cutout) on top
   * of the padding declared in the layout. The bar itself keeps the full width and reaches the
   * bottom edge, so its background runs behind the navigation bar. Safe to call repeatedly.
   *
   * @param bar The bottom bar container. If null, the method does nothing.
   */
  public static void applyBottomBarInsets(View bar) {
    if (bar == null) {
      return;
    }

    Rect base;
    if (bar.getTag(R.id.tag_bottom_bar_base_padding) instanceof Rect r) {
      base = r;
    } else {
      base =
          new Rect(
              bar.getPaddingLeft(),
              bar.getPaddingTop(),
              bar.getPaddingRight(),
              bar.getPaddingBottom());
      bar.setTag(R.id.tag_bottom_bar_base_padding, base);
    }

    Insets insets = Insets.NONE;
    WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(bar);
    if (windowInsets != null) {
      insets =
          windowInsets.getInsets(
              WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    }

    bar.setPadding(
        base.left + insets.left, base.top, base.right + insets.right, base.bottom + insets.bottom);
  }

  /**
   * Adjusts the top margin of the given TextView to account for the status bar's height, while also
   * including an additional base margin specified in dp. The method calculates the status bar
   * height using system insets and combines it with the provided base margin before applying the
   * resulting value to the TextView's top margin.
   *
   * @param textView The TextView whose top margin should be adjusted. If null, the method does
   *     nothing.
   * @param baseMarginDp The base margin in dp to be added to the status bar's height. This value is
   *     converted to pixels before being applied.
   */
  public static void adjustTextViewTopMarginForStatusBar(TextView textView, int baseMarginDp) {
    if (textView == null) {
      return;
    }

    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) textView.getLayoutParams();
    if (params == null) {
      return;
    }

    int topInset = 0;
    WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(textView);
    if (windowInsets != null) {
      topInset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
    }

    // Convert dp to pixels
    float density = textView.getResources().getDisplayMetrics().density;
    int baseMarginPx = (int) (baseMarginDp * density);

    params.topMargin = baseMarginPx + topInset;
    textView.setLayoutParams(params);
  }

  /**
   * Displays a short message using the provided string and duration. If Accessibility Mode is
   * enabled, the message is announced via the device's screen reader instead. Otherwise it appears
   * above the bottom bar of the foreground activity, or as a toast when there is none. Ensures the
   * use of application context to prevent memory leaks or context-related issues. If the context or
   * message is null, the method does nothing.
   *
   * @param context The context from which the toast is triggered. If null, no action is taken.
   * @param message The string message to display in the toast. If null, no action is taken.
   * @param duration The duration for which the toast should be displayed. Should be either
   *     Toast.LENGTH_SHORT or Toast.LENGTH_LONG.
   */
  @SuppressWarnings("deprecation")
  public static void showToast(Context context, String message, int duration) {
    if (context == null || message == null) {
      return;
    }

    // Always use the application context to prevent memory leaks and context-related issues
    Context appContext = context.getApplicationContext();

    // If Accessibility Mode is enabled, announce via screen reader instead of showing a toast
    try {
      SharedPreferences prefs =
          appContext.getSharedPreferences("export_options", Context.MODE_PRIVATE);
      boolean a11yMode =
          prefs.getBoolean(
              de.schliweb.makeacopy.ui.options.OptionsDialogFragment.BUNDLE_ACCESSIBILITY_MODE,
              false);
      Log.d(TAG, "Accessibility Mode: " + a11yMode);
      if (a11yMode) {
        AccessibilityManager am =
            (AccessibilityManager) appContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled()) {
          AccessibilityEvent event =
              AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
          event.setPackageName(appContext.getPackageName());
          event.setClassName(UIUtils.class.getName());
          event.getText().add(message);
          am.sendAccessibilityEvent(event);
          Log.d(TAG, "Accessibility announcement made: " + message);
          return; // Do not show a Toast when A11y announcement is made
        }
      }
    } catch (Throwable ignore) {
      Log.e(TAG, "Error checking accessibility mode", ignore);
      // Best-effort: fall back to Toast below
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
      showMessage(appContext, message, duration, true);
    } else {
      new Handler(Looper.getMainLooper())
          .post(() -> showMessage(appContext, message, duration, true));
    }
  }

  /**
   * Remembers the resumed activity, so that messages can be shown inside its window. Call once from
   * {@link Application#onCreate()}.
   */
  public static void trackForegroundActivity(Application application) {
    application.registerActivityLifecycleCallbacks(
        new Application.ActivityLifecycleCallbacks() {
          @Override
          public void onActivityResumed(Activity activity) {
            foregroundActivity = new WeakReference<>(activity);
          }

          @Override
          public void onActivityPaused(Activity activity) {
            if (foregroundActivity.get() == activity) {
              foregroundActivity.clear();
            }
          }

          @Override
          public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

          @Override
          public void onActivityStarted(Activity activity) {}

          @Override
          public void onActivityStopped(Activity activity) {}

          @Override
          public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

          @Override
          public void onActivityDestroyed(Activity activity) {}
        });
  }

  /**
   * Shows the message above the bottom bar of the foreground activity. The system decides where a
   * toast appears, and on some devices that is on top of the bottom bar. A toast is still used when
   * the app is not in the foreground or a dialog covers the activity.
   */
  private static void showMessage(
      Context appContext, String message, int duration, boolean mayRetry) {
    try {
      if (showAboveBottomBar(foregroundActivity.get(), message, duration)) {
        return;
      }
    } catch (Throwable t) {
      Log.w(TAG, "In-app message failed, falling back to a toast", t);
      mayRetry = false;
    }
    if (mayRetry) {
      // Right after a file picker or a dialog closes, the activity has not got its window focus
      // back yet. Give it a moment before settling for a toast.
      new Handler(Looper.getMainLooper())
          .postDelayed(
              () -> showMessage(appContext, message, duration, false), MESSAGE_RETRY_DELAY_MS);
      return;
    }
    Toast.makeText(appContext, message, duration).show();
  }

  private static boolean showAboveBottomBar(Activity activity, String message, int duration) {
    if (activity == null
        || activity.isFinishing()
        || activity.isDestroyed()
        || !activity.hasWindowFocus()) {
      return false;
    }
    View content = activity.findViewById(android.R.id.content);
    if (content == null) {
      return false;
    }

    Snackbar snackbar =
        Snackbar.make(
            content, message, duration == Toast.LENGTH_LONG ? MESSAGE_LONG_MS : MESSAGE_SHORT_MS);
    TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
    if (text != null) {
      text.setMaxLines(MESSAGE_MAX_LINES);
    }
    snackbar.setAnchorView(findMessageAnchor(content));
    snackbar.setAnchorViewLayoutListenerEnabled(true);

    // Follow the bottom bar when the screen changes while the message is visible
    ViewTreeObserver.OnGlobalLayoutListener reanchor =
        () -> {
          View bar = findMessageAnchor(content);
          if (bar != snackbar.getAnchorView()) {
            snackbar.setAnchorView(bar);
            snackbar.setAnchorViewLayoutListenerEnabled(true);
            snackbar.getView().requestLayout();
          }
        };
    snackbar.addCallback(
        new BaseTransientBottomBar.BaseCallback<Snackbar>() {
          @Override
          public void onShown(Snackbar transientBottomBar) {
            content.getViewTreeObserver().addOnGlobalLayoutListener(reanchor);
          }

          @Override
          public void onDismissed(Snackbar transientBottomBar, int event) {
            content.getViewTreeObserver().removeOnGlobalLayoutListener(reanchor);
          }
        });
    snackbar.show();
    return true;
  }

  /**
   * Finds the view a message has to stay clear of: the bottom bar, or the topmost row of controls
   * stacked directly on it, such as the tool row of the crop screen. A message blocks touches, so
   * it must not sit on any of them.
   */
  private static View findMessageAnchor(View root) {
    View anchor = findBottomBar(root);
    if (anchor == null || !(anchor.getParent() instanceof ViewGroup parent)) {
      return anchor;
    }
    float density = root.getResources().getDisplayMetrics().density;
    int maxGap = (int) (ACTION_ROW_MAX_GAP_DP * density);
    int maxHeight = (int) (ACTION_ROW_MAX_HEIGHT_DP * density);
    boolean moved = true;
    while (moved) {
      moved = false;
      for (int i = 0; i < parent.getChildCount(); i++) {
        View child = parent.getChildAt(i);
        int gap = anchor.getTop() - child.getBottom();
        if (child != anchor
            && child.getVisibility() == View.VISIBLE
            && child.getHeight() > 0
            && child.getHeight() <= maxHeight
            && child.getTop() < anchor.getTop()
            && Math.abs(gap) <= maxGap
            && hasClickable(child)) {
          anchor = child;
          moved = true;
          break;
        }
      }
    }
    return anchor;
  }

  private static boolean hasClickable(View v) {
    if (v.getVisibility() != View.VISIBLE) {
      return false;
    }
    if (v.isClickable()) {
      return true;
    }
    if (v instanceof ViewGroup group) {
      for (int i = 0; i < group.getChildCount(); i++) {
        if (hasClickable(group.getChildAt(i))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Finds the visible bottom bar that reaches highest up the screen. Bottom bars are recognised by
   * the tag that {@link #applyBottomBarInsets(View)} leaves on them.
   */
  private static View findBottomBar(View root) {
    View best = null;
    int bestTop = Integer.MAX_VALUE;
    java.util.ArrayDeque<View> pending = new java.util.ArrayDeque<>();
    pending.add(root);
    int[] location = new int[2];
    while (!pending.isEmpty()) {
      View v = pending.poll();
      if (v.getVisibility() != View.VISIBLE) {
        continue;
      }
      if (v.getTag(R.id.tag_bottom_bar_base_padding) != null) {
        v.getLocationInWindow(location);
        if (v.getHeight() > 0 && location[1] < bestTop) {
          best = v;
          bestTop = location[1];
        }
      } else if (v instanceof ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
          pending.add(group.getChildAt(i));
        }
      }
    }
    return best;
  }

  /**
   * Displays a toast message using a string resource ID and a specified duration. The method
   * resolves the resource string and displays it as a toast. The application context is used
   * internally to ensure memory safety and avoid context-related issues. If the context is null or
   * the resource ID cannot be resolved, the method does nothing.
   *
   * @param context The context from which the toast is triggered. If null, no action is taken.
   * @param resId The resource ID of the string to display in the toast. If the resource ID cannot
   *     be resolved, no action is taken.
   * @param duration The duration for which the toast should be displayed. Should be either
   *     Toast.LENGTH_SHORT or Toast.LENGTH_LONG.
   */
  public static void showToast(Context context, int resId, int duration) {
    if (context == null) {
      return;
    }

    // Always use the application context to prevent memory leaks and context-related issues
    Context appContext = context.getApplicationContext();

    // Resolve string now to funnel through the same accessibility path
    String msg;
    try {
      // The application context does not follow the in-app language on Android 12 and older
      msg = AppLanguage.localize(appContext).getString(resId);
    } catch (Throwable t) {
      msg = null;
    }
    if (msg != null) {
      showToast(appContext, msg, duration);
    }
  }
}
