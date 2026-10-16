package com.airsky.browser;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import android.view.View;
import android.widget.ListView;

public class ResetFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.backupreset, rootKey);

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ✅ 見切れ対策のためにパディングを追加
        View rootView = getView();
        if (rootView != null) {
            ListView listView = (ListView) rootView.findViewById(android.R.id.list);
            if (listView != null) {
                int statusBarHeight = getStatusBarHeight();
                listView.setPadding(0, statusBarHeight, 0, 0);
                listView.setClipToPadding(false);
            }
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}

