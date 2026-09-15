package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.Application;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerDbHelper;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerFileStore;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MessagingApi;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MockMessagingApi;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MockRealtimeChannel;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RealtimeChannel;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RestContract;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RestMessagingApi;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.SyncEngine;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.settings.MessengerSettings;
import com.appfactory.modules.network.Network;
import com.appfactory.modules.network.NetworkWatcher;
import com.appfactory.modules.settings.PrefsStore;

/**
 * App-wide dependency wiring. Everything is replaceable: the backend is a
 * deterministic mock unless the user configures a HTTPS REST endpoint, the
 * realtime channel is a deterministic mock queue, and the store persists JSON
 * snapshots to internal storage.
 */
public final class App extends Application {

    private static volatile App instance;

    private MessengerSettings settings;
    private MessengerDbHelper db;
    private MessengerFileStore store;
    private MessengerRepository repo;
    private Network network;
    private NetworkWatcher watcher;
    private MessagingApi api;
    private RealtimeChannel channel;
    private SyncEngine engine;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        settings = MessengerSettings.withProvider(
                new PrefsStore(getSharedPreferences("messenger", MODE_PRIVATE)));

        db = new MessengerDbHelper(this);
        db.getWritableDatabase(); // create/migrate schema eagerly

        store = new MessengerFileStore(getFilesDir());
        repo = initializeRepository();
        repo.attach(store);

        network = new Network();
        watcher = new NetworkWatcher(this, network);

        api = createApi();
        channel = new MockRealtimeChannel();
        channel.open();

        engine = new SyncEngine(repo, api, channel, network);
        watcher.refresh();
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            watcher.startWatching();
        }
        syncNow();
    }

    private MessengerRepository initializeRepository() {
        String snap = store.readSnapshot();
        if (snap != null) {
            try {
                return MessengerRepository.restore(snap, "me");
            } catch (RuntimeException ignored) {
                // corrupted snapshot: fall back to a fresh seeded state
            }
        }
        return seedDemo(new MessengerRepository("me"));
    }

    private MessagingApi createApi() {
        String base = settings.apiBaseUrl();
        if (base.isEmpty()) return new MockMessagingApi();
        return new RestMessagingApi(new RestContract(base, settings.authToken()));
    }

    /** Deterministic first-run demo data (no outbox ops are enqueued). */
    public static MessengerRepository seedDemo(MessengerRepository repo) {
        long now = System.currentTimeMillis();
        long day = 86_400_000L;

        repo.putContact(Contact.create("c1", "Ada Lovelace", "+1 555 0101",
                "Analytical engine pioneer", now - 12 * day));
        repo.putContact(Contact.create("c2", "Grace Hopper", "+1 555 0102",
                "Compiler captain", now - 9 * day));
        repo.putContact(Contact.create("c3", "Alan Turing", "+1 555 0103",
                "The Enigma of computing", now - 7 * day));

        Conversation ada = repo.addDirectConversation("c1", "Ada Lovelace");
        repo.upsert(ada.muted(true));
        repo.incoming(Message.text("seed1", ada.id, "c1",
                "Hi! Did you see the encryption design notes?", now - day));
        repo.incoming(Message.text("seed2", ada.id, "c1",
                "I moved the vault wrapper to SecretBox. Ready for review.", now - day + 1));
        repo.incoming(Message.text("seed3", ada.id, "c1",
                "Also: forwarded you the group invite link.", now - day + 2));

        Conversation grace = repo.addDirectConversation("c2", "Grace Hopper");
        repo.upsert(grace.pinned(true));
        repo.incoming(Message.text("seed4", grace.id, "c2",
                "The offline outbox queue works like a compiled message buffer.", now - 3 * 60_000));
        repo.incoming(Message.text("seed5", grace.id, "c2",
                "Ship it once, debug it twice. Or ship twice.", now - 2 * 60_000));
        repo.incoming(Message.attach(
                "seed6", grace.id, "c2", Message.Kind.IMAGE, "Proof it builds",
                Attachment.create("att1", Message.Kind.IMAGE, "content://app/.demo/compiler.jpg",
                        "image/jpeg", 0, 1280, 720, 0), now - 60_000));

        Conversation group = repo.addGroupConversation("Fast Lane Lab");
        repo.upsert(group);
        repo.incoming(Message.text("seed7", group.id, "c3",
                "Group chat is live. Members: us.", now - 30 * 60_000));
        repo.incoming(Message.text("seed8", group.id, "c1",
                "Pin the spec, mute the chit-chat.", now - 20 * 60_000));
        repo.incoming(Message.text("seed9", group.id, "c2",
                "Unread counts are aggregated here too. Numbers don't lie.", now - 10 * 60_000));

        Conversation archived = repo.addDirectConversation("c3", "Alan Turing");
        repo.upsert(archived.archived(true));
        repo.incoming(Message.text("seed10", archived.id, "c3",
                "Can machines think? Probably not on release day.", now - 5 * day));

        return repo;
    }

    public MessengerSettings settings() {
        return settings;
    }

    public MessengerDbHelper db() {
        return db;
    }

    public MessengerFileStore store() {
        return store;
    }

    public MessengerRepository repo() {
        return repo;
    }

    public Network network() {
        return network;
    }

    public NetworkWatcher watcher() {
        return watcher;
    }

    public SyncEngine syncEngine() {
        return engine;
    }

    /** Kick a sync pass off the calling thread (safe from any thread). */
    public void syncNow() {
        final SyncEngine e = this.engine;
        Thread t = new Thread(() -> {
            try {
                e.sync();
            } catch (RuntimeException ignored) {
                // sync is best-effort on-device
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public void refreshNetworkState() {
        watcher.refresh();
    }
}