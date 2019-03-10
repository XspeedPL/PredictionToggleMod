package xeed.xposed.xkptgl;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import xeed.library.xposed.BaseModule;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

public final class PredictionToggle extends BaseModule {
    private final CTSData[] mData = new CTSData[4];
    private String[] mPredKeys, mPredVals;
    private Constructor<?> mSettingsCtr;
    private InputMethodService mInSvc;
    private int mLayScale = 90, mTxtScale = 80;

    @Override
    public final long getVersion() {
        return BuildConfig.VERSION_CODE;
    }

    @Override
    protected final String getLogTag() {
        return "XKPT";
    }

    @Override
    public final String getMainPackage() {
        return "com.sonyericsson.textinput.uxp";
    }

    @Override
    protected final boolean shouldHookPWM() {
        return false;
    }

    @Override
    public final void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (getMainPackage().equals(param.packageName)) {
            log("Defining package hooks");
            Class<?> cK = findClass("com.sonyericsson.textinput.uxp.model.keyboard.Key", param.classLoader);
            Class<?> cCPS = findClass("com.sonyericsson.ned.model.CodePointString", param.classLoader);
            Class<?> cPS = tryFindClass(param.classLoader, "com.sonyericsson.textinput.uxp.model.settings.PersistentSettings", "com.sonyericsson.textinput.uxp.model.settings.Settings");
            mSettingsCtr = cPS.getConstructor(Context.class);
            Class<?> cIMSG = findClass("com.sonyericsson.textinput.uxp.glue.InputMethodServiceGlue", param.classLoader);
            XposedBridge.hookAllMethods(cIMSG, "onCreate", handleIMSG);
            Class<?> cKCP = tryFindClass(param.classLoader, "com.sonyericsson.textinput.uxp.view.KeyCandidatesPopup", "com.sonyericsson.textinput.uxp.view.KeyPopup");
            findAndHookMethod(cKCP, "hasSelectedCandidate", handleHSC);
            findAndHookMethod(cK, "createKeyCandidates", handleCKC);
            Class<?> cKCTI = findClass("com.sonyericsson.textinput.uxp.view.KeyCandidateTextItem", param.classLoader);
            XposedBridge.hookAllConstructors(cKCTI, handleKCTI);
            XposedBridge.hookAllMethods(cKCP, "createCandidates", handleCC);

            CTSData.mEmpty = toCPS(cCPS, "");
            for (int i = 0; i < 4; ++i)
                mData[i] = new CTSData(cCPS, i);
        }

        super.handleLoadPackage(param);
    }

    @Override
    protected final void reloadPrefs(Intent in) {
        mLayScale = mPrefs.getInt("scaleLayout", 90);
        mTxtScale = mPrefs.getInt("scaleText", 80);
        for (int i = 0; i < 4; ++i)
            mData[i].mShow = mPrefs.getBoolean("show" + i, true);
    }

    private final XC_MethodHook handleKCTI = new XC_MethodHook() {
        @Override
        protected final void afterHookedMethod(MethodHookParam mhp) {
            TextView tv = (TextView) mhp.args[0];
            if (tv.getText().length() == 4) {
                LayoutParams lp = tv.getLayoutParams();
                lp.width = lp.width * mLayScale / 100;
                tv.setTextScaleX(tv.getTextScaleX() * mTxtScale / 100F);
                tv.requestLayout();
            }
        }
    };

    private final XC_MethodHook handleIMSG = new XC_MethodHook() {
        @Override
        protected final void afterHookedMethod(MethodHookParam mhp) {
            mInSvc = (InputMethodService) mhp.thisObject;
            registerWithContext(mInSvc);
            log("Service instance acquired");

            try {
                Resources uxpRes = mInSvc.getPackageManager().getResourcesForApplication(getMainPackage());
                int keys = uxpRes.getIdentifier("prediction_entries", "array", getMainPackage());
                int vals = uxpRes.getIdentifier("prediction_entry_values", "array", getMainPackage());

                mPredKeys = uxpRes.getStringArray(keys);
                mPredVals = uxpRes.getStringArray(vals);
            } catch (PackageManager.NameNotFoundException e) {
                log("Resource resolution failed!");
            }
        }
    };

    private static final XC_MethodHook handleCC = new XC_MethodHook() {
        @Override
        protected final void beforeHookedMethod(MethodHookParam mhp) {
            if (isSpecialKey(mhp.args[1]))
                mhp.setResult(mhp.args[0]);
        }
    };

    private static boolean isSpecialKey(Object key) {
        Object kc = getObjectField(key, "mNormalKeyCandidates");
        Object cps = getObjectField(kc, "primaryCandidate");
        return cps != null && " ".equals(fromCPS(cps));
    }

    private final XC_MethodHook handleHSC = new XC_MethodHook() {
        @Override
        protected final void beforeHookedMethod(MethodHookParam mhp) throws ReflectiveOperationException {
            Object key = callMethod(mhp.thisObject, "getSelectedCandidate");
            if (key == null) return;
            Object cps = callMethod(key, "getPrimaryCandidate", false);
            if (cps != null) {
                String s = fromCPS(cps);
                if (s.startsWith(".") && s.length() == 2) {
                    mhp.setResult(false);
                    Object ps = mSettingsCtr.newInstance(mInSvc);
                    Object ed = callMethod(ps, "edit");
                    int i = Integer.parseInt(s.substring(1));
                    Toast.makeText(mInSvc, mPredKeys[i], Toast.LENGTH_SHORT).show();
                    callMethod(ed, "setPredictionMode", mPredVals[i]);
                    dlog("Special key was selected, new mode: " + mPredVals[i]);
                    callMethod(ed, "commit");
                    mInSvc.onStartInputView((EditorInfo) getObjectField(mInSvc, "mEditorInfo"), false);
                }
            }
        }
    };

    private static Object toCPS(Class<?> cpsClass, String value) {
        return callStaticMethod(cpsClass, "create", value);
    }

    private static String fromCPS(Object cps) {
        return (String) getObjectField(cps, "mString");
    }

    private final XC_MethodHook handleCKC = new XC_MethodHook() {
        @Override
        protected final void afterHookedMethod(MethodHookParam mhp) throws ReflectiveOperationException {
            if (isSpecialKey(mhp.thisObject)) {
                setIntField(mhp.thisObject, "mLabelStyle", 2);
                addState(mhp.thisObject);
            }
        }
    };

    private void addState(Object key) throws ReflectiveOperationException {
        Object ps = mSettingsCtr.newInstance(mInSvc);
        String s = (String) callMethod(ps, "getSoftwareKeyboardPrediction");
        for (int i = 0; i < 4; ++i) {
            CTSData data = mData[i];
            if (s.equals(mPredVals[i])) {
                dlog("Setting " + s + " as secondary print");
                callMethod(key, "addSecondaryPrint", CTSData.mEmpty);
                callMethod(key, "addShiftedSecondaryPrint", CTSData.mEmpty);
                callMethod(key, "addVisualSecondaryPrint", data.mCandidate);
                callMethod(key, "addShiftedVisualSecondaryPrint", data.mCandidate);
            } else if (mData[i].mShow) addCTS(key, data);
        }
    }

    private void addCTS(Object key, CTSData data) {
        dlog("Adding candidate to key: " + data.mDesc);
        callMethod(key, "addCandidate", data.mCandidateVis, data.mCandidate);
        callMethod(key, "addShiftedCandidate", data.mCandidateVis, data.mCandidate);
    }

    private static class CTSData {
        static Object mEmpty;

        Object mCandidateVis, mCandidate;
        String mDesc;
        boolean mShow;

        CTSData(Class<?> cpsClass, int mode) {
            mDesc = Utils.getVal(mode);
            mCandidate = toCPS(cpsClass, mDesc);
            mCandidateVis = callStaticMethod(cpsClass, "create", "." + Integer.toString(mode));
        }
    }
}
