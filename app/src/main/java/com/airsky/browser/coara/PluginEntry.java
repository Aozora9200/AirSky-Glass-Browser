package com.airsky.browser.coara;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PluginEntry {
    public String runAt = PluginInfo.RUN_AT_END;
    public boolean allFrames = false;
    public final List<String> matches = new ArrayList<>();
    public final List<String> excludes = new ArrayList<>();
    public final List<String> scripts = new ArrayList<>();
    public final List<String> styles = new ArrayList<>();
    private transient List<String> cachedMatchSource;
    private transient List<String> cachedExcludeSource;
    private transient List<String> cachedMatchRegex;
    private transient List<String> cachedExcludeRegex;

    public boolean isTarget(String url) {
        if (url == null) {
            return false;
        }
        List<String> ex = excludeRegex();
        if (!ex.isEmpty() && PluginPatterns.matchesAny(ex, url)) {
            return false;
        }
        return PluginPatterns.matchesAny(matchRegex(), url);
    }

    private List<String> matchRegex() {
        List<String> source = PluginPatterns.effectiveMatches(matches);
        if (cachedMatchSource == null || !cachedMatchSource.equals(source)) {
            cachedMatchSource = new ArrayList<>(source);
            cachedMatchRegex = PluginPatterns.toRegexList(source);
        }
        return cachedMatchRegex;
    }

    private List<String> excludeRegex() {
        if (cachedExcludeSource == null || !cachedExcludeSource.equals(excludes)) {
            cachedExcludeSource = new ArrayList<>(excludes);
            cachedExcludeRegex = PluginPatterns.toRegexList(excludes);
        }
        return cachedExcludeRegex;
    }

    public boolean hasContent() {
        return !scripts.isEmpty() || !styles.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("runAt", runAt);
        o.put("allFrames", allFrames);
        o.put("matches", toArray(matches));
        o.put("excludes", toArray(excludes));
        o.put("scripts", toArray(scripts));
        o.put("styles", toArray(styles));
        return o;
    }

    public static PluginEntry fromJson(JSONObject o) {
        PluginEntry e = new PluginEntry();
        e.runAt = PluginInfo.normalizeRunAt(o.optString("runAt", PluginInfo.RUN_AT_END));
        e.allFrames = o.optBoolean("allFrames", false);
        PluginInfo.readArray(o.optJSONArray("matches"), e.matches);
        PluginInfo.readArray(o.optJSONArray("excludes"), e.excludes);
        PluginInfo.readArray(o.optJSONArray("scripts"), e.scripts);
        PluginInfo.readArray(o.optJSONArray("styles"), e.styles);
        return e;
    }

    public PluginEntry copy() {
        PluginEntry e = new PluginEntry();
        e.runAt = runAt;
        e.allFrames = allFrames;
        e.matches.addAll(matches);
        e.excludes.addAll(excludes);
        e.scripts.addAll(scripts);
        e.styles.addAll(styles);
        return e;
    }

    private static JSONArray toArray(List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) {
            a.put(s);
        }
        return a;
    }
}
