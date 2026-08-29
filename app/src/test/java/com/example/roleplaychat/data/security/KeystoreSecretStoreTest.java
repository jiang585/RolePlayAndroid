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
}
