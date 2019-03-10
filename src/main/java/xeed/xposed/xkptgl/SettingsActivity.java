package xeed.xposed.xkptgl;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import xeed.library.ui.BaseSettings;

public final class SettingsActivity extends BaseSettings {
    private static final String UXP_PACKAGE_NAME = "com.sonyericsson.textinput.uxp";

    @Override
    protected final void onCreatePreferences(PreferenceManager frag) {
        addPreferencesToCategory(R.xml.settings, Category.general);

        String[] entries = {"", "", "", ""};
        try {
            Resources uxpRes = getPackageManager().getResourcesForApplication(UXP_PACKAGE_NAME);
            int resId = uxpRes.getIdentifier("prediction_entries", "array", UXP_PACKAGE_NAME);
            entries = uxpRes.getStringArray(resId);
        } catch (PackageManager.NameNotFoundException ignored) {
            Toast.makeText(this, R.string.diag_pkg_err, Toast.LENGTH_LONG).show();
        }

        for (int i = 0; i < 4; ++i) {
            Preference pref = frag.findPreference("show" + i);
            pref.setTitle(getString(R.string.pref_showX_t) + " " + Utils.getVal(i));
            pref.setSummary(getString(R.string.pref_showX_s) + " \"" + entries[i] + "\"");
        }
    }
}
