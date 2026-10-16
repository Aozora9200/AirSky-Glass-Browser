package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeoSettingsActivity extends AppCompatActivity {

    private ListView listView;
    private SharedPreferences prefs;
    private List<Map.Entry<String, Boolean>> siteList = new ArrayList<>();
    private SiteAdapter adapter;
    private TextView tvEmpty;

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;
    private ImageView Background;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geowebsite_list);
        TouchEffectView.attach(getWindow());
        Background = findViewById(R.id.background);
        applySavedBackground();
        applyBackTheme();

        tvEmpty = findViewById(R.id.emptyView);

        listView = findViewById(R.id.siteListView);
        prefs = getSharedPreferences("GeoPermissionStore", MODE_PRIVATE);

        loadSites();

        adapter = new SiteAdapter(this, siteList, prefs);
        listView.setAdapter(adapter);
        if (siteList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
        // Action Bar が表示されているか確認
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void applySavedBackground() {
        // 壁紙はスタートページと同じもの(時間帯で変わる既定の壁紙・設定した画像)
        AppWallpaper.apply(findViewById(R.id.backgroundImage), Background);
    }


    private void applyBackTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int theme = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        int black = getResources().getColor(R. color. backgroundBlack);
        int white = getResources().getColor(R. color. backgroundWhite);

        switch (theme) {
            case THEME_LIGHT:
                Background.setBackgroundColor(white);
                break;
            case THEME_DARK:
                Background.setBackgroundColor(black);
                break;
            case THEME_SYSTEM:
            default:
                // OS 側の設定に従う
                int nightModeFlags = getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    Background.setBackgroundColor(black);
                } else {
                    Background.setBackgroundColor(white);
                }
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadSites() {
        siteList.clear();
        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                siteList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), (Boolean) entry.getValue()));
            }
        }
    }

    // ---- カスタムアダプター ----
    private static class SiteAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final List<Map.Entry<String, Boolean>> data;
        private final SharedPreferences prefs;

        SiteAdapter(Context context, List<Map.Entry<String, Boolean>> data, SharedPreferences prefs) {
            this.inflater = LayoutInflater.from(context);
            this.data = data;
            this.prefs = prefs;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_geosite_switch, parent, false);
                holder = new ViewHolder();
                holder.urlText = convertView.findViewById(R.id.siteUrlText);
                holder.switchView = convertView.findViewById(R.id.siteSwitch);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Map.Entry<String, Boolean> item = data.get(position);
            holder.urlText.setText(item.getKey());
            holder.switchView.setChecked(item.getValue());

            // スイッチの変更時にSharedPreferencesを更新
            holder.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(item.getKey(), isChecked).apply();
                data.set(position, new AbstractMap.SimpleEntry<>(item.getKey(), isChecked));
            });

            return convertView;
        }

        static class ViewHolder {
            TextView urlText;
            Switch switchView;
        }
    }
}