package dev.joseph.hyperbounce;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import dev.joseph.hyperbounce.ui.ComposeBridge;
import dev.joseph.hyperbounce.ui.ExtractedTab;
import kotlin.Unit;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;
                stockBar.setAlpha(0f);

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        stockBar.post(() -> executeUIHeist(stockBar));

                        stockBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                if (stockBar.getVisibility() != View.GONE) stockBar.setVisibility(View.GONE);
                                ViewParent p = stockBar.getParent();
                                if (p instanceof View && ((View) p).getVisibility() != View.GONE) {
                                    ((View) p).setVisibility(View.GONE);
                                }
                                return true;
                            }
                        });
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });
    }

    private void executeUIHeist(ViewGroup stockBar) {
        try {
            ViewGroup rootView = (ViewGroup) stockBar.getRootView();
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; 

            Context context = stockBar.getContext();
            List<ExtractedTab> extractedTabs = new ArrayList<>();
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            
            if (menuContainer != null) {
                int tabCount = menuContainer.getChildCount();
                for (int i = 0; i < tabCount; i++) {
                    View stockTab = menuContainer.getChildAt(i);
                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);
                    if (nativeIcon == null || nativeText == null) continue;
                    
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    String label = nativeText.getText().toString();
                    
                    extractedTabs.add(new ExtractedTab(label, clonedDrawable, i, stockTab));
                }
            }

            // PASSING ROOT VIEW: Pipe the extracted tabs and the DecorView into the Jetpack Compose Bridge
            View composeFloatingBar = ComposeBridge.createFloatingBar(context, rootView, extractedTabs, index -> {
                extractedTabs.get(index).getStockView().performClick();
                return Unit.INSTANCE;
            });

            composeFloatingBar.setTag("HyperFloatingBar");

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 130;

            rootView.addView(composeFloatingBar, lp);

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Compose Injection Exception: " + e.getMessage());
        }
    }

    private ImageView findImageView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof ImageView) return (ImageView) v;
            if (v instanceof ViewGroup) {
                ImageView iv = findImageView((ViewGroup) v);
                if (iv != null) return iv;
            }
        }
        return null;
    }

    private TextView findTextView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) return (TextView) v;
            if (v instanceof ViewGroup) {
                TextView tv = findTextView((ViewGroup) v);
                if (tv != null) return tv;
            }
        }
        return null;
    }
}
