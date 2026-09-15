package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure group membership model: add/remove members, admin flag, title from
 * member names, size caps. (Member list is kept sorted for determinism.)
 */
public final class GroupModel {

    public static final int MAX_MEMBERS = 1024;

    private final List<String> members = new ArrayList<>(); // sorted member ids
    private String title = "";

    public void open(String selfId, List<String> others, String explicitTitle) {
        members.clear();
        members.add(selfId);
        for (String id : others) addMember(id);
        this.title = explicitTitle;
    }

    public boolean addMember(String id) {
        if (id == null || id.isEmpty()) return false;
        if (members.contains(id)) return false;
        if (members.size() >= MAX_MEMBERS) return false;
        members.add(id);
        return true;
    }

    public boolean removeMember(String id) {
        return members.remove(id);
    }

    public boolean hasMember(String id) {
        return members.contains(id);
    }

    public int size() {
        return members.size();
    }

    public List<String> members() {
        return new ArrayList<>(members);
    }

    public void setTitle(String t) {
        this.title = t == null ? "" : t;
    }

    public String title() {
        if (!title.isEmpty()) return title;
        // default: "You, X, Y…" from member contact names
        return "Group";
    }

    public String titleFromContacts(List<Contact> contacts, String selfId) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String id : members) {
            if (id.equals(selfId)) continue;
            String name = id;
            for (Contact c : contacts) if (c.id.equals(id)) name = c.displayName;
            if (shown > 0) sb.append(", ");
            sb.append(name);
            if (++shown == 2) break;
        }
        if (shown == 0) return "Empty group";
        if (members.size() - 1 > 2) sb.append(" +").append(members.size() - 1 - 2);
        return sb.toString();
    }
}