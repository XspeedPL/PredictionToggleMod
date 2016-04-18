package xeed.xposed.xkptgl;

import android.preference.PreferenceManager;
import xeed.library.ui.BaseSettings;

public final class SettingsActivity extends BaseSettings
{
	public static final String getVal(final int i)
	{
		if (i == 0) return "Auto";
		else if (i == 1) return "Semi";
		else if (i == 2) return "Manu";
		else return "None";
	}

	@Override
    protected final void onCreatePreferences(final PreferenceManager frag)
	{
	    addPreferencesToCategory(R.xml.settings, Category.general);
        for (int i = 0; i < 4; ++i)
            frag.findPreference("show" + i).setTitle(getString(R.string.pref_showX_t) + ' ' + getVal(i));
	}
	
    @Override
    protected final String getPrefsName() { return "xkptsettings"; }
}
