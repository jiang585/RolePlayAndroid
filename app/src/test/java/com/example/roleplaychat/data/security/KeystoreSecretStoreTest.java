package com.example.roleplaychat.data.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeystoreSecretStoreTest {

    @Test
    public void unavailableKeystore_usesOnlyVolatileStorage() {
        KeystoreSecretStore store = new KeystoreSecretStore((android.content.SharedPreferences) null);

        store.putSecret("api_key", "secret");
        assertTrue(store.hasSecret("api_key"));
        assertEquals("secret", store.getSecret("api_key"));

        store.removeSecret("api_key");
        assertFalse(store.hasSecret("api_key"));
    }

    @Test
    public void multipleAliases_areIsolated() {
        KeystoreSecretStore store = new KeystoreSecretStore((android.content.SharedPreferences) null);

        store.putSecret("api_key_profile-a", "key-a");
        store.putSecret("api_key_profile-b", "key-b");

        assertEquals("key-a", store.getSecret("api_key_profile-a"));
        assertEquals("key-b", store.getSecret("api_key_profile-b"));

        // 删除一个别名不影响另一个
        store.removeSecret("api_key_profile-a");
        assertFalse(store.hasSecret("api_key_profile-a"));
        assertTrue(store.hasSecret("api_key_profile-b"));
        assertEquals("key-b", store.getSecret("api_key_profile-b"));
    }
}
