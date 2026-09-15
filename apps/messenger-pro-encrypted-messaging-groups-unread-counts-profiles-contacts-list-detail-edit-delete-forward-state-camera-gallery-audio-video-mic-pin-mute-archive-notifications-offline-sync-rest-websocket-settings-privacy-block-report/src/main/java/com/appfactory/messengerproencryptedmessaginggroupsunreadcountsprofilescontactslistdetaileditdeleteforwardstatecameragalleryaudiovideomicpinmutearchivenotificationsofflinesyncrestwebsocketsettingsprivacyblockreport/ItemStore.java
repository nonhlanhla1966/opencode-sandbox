package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import com.appfactory.modules.json.Json;
import com.appfactory.modules.json.JsonArray;
import com.appfactory.modules.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline-first store with deterministic add/remove/search + JSON. */
public final class ItemStore {
    private final List<Item> items = new ArrayList<>();
    private int nextId = 1;

    public Item add(String title, String detail) {
        Item it = new Item(String.valueOf(nextId++), title, detail,
                           System.currentTimeMillis());
        items.add(it);
        return it;
    }
    public boolean remove(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                return true;
            }
        }
        return false;
    }
    public List<Item> items() { return Collections.unmodifiableList(items); }
    public int size() { return items.size(); }
    public List<Item> search(String q) {
        if (q == null || q.isEmpty()) return items();
        List<Item> out = new ArrayList<>();
        String needle = q.toLowerCase();
        for (Item it : items) {
            if (it.title.toLowerCase().contains(needle)
                    || it.detail.toLowerCase().contains(needle)) out.add(it);
        }
        return out;
    }
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("nextId", nextId);
        JsonArray arr = new JsonArray();
        for (Item it : items) {
            JsonObject e = new JsonObject();
            e.put("id", it.id);
            e.put("title", it.title);
            e.put("detail", it.detail);
            e.put("createdAt", it.createdAt);
            arr.add(e);
        }
        o.put("items", arr);
        return o;
    }
    public static ItemStore fromJson(String text) {
        ItemStore s = new ItemStore();
        if (text == null || text.isEmpty()) return s;
        JsonObject o = Json.parseObject(text);
        s.nextId = o.getInt("nextId", 1);
        JsonArray arr = o.getArray("items");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject e = arr.getObject(i);
            s.items.add(new Item(e.getString("id"), e.getString("title"),
                    e.getString("detail"), e.getLong("createdAt", 0)));
        }
        return s;
    }
}
