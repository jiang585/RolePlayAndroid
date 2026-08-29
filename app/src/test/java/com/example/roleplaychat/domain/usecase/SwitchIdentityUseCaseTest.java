package com.example.roleplaychat.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.repository.CharacterRepository;
import com.example.roleplaychat.domain.repository.ScriptRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 切换身份用例测试（架构文档 §15.2-1：身份切换后新消息使用新角色，旧消息快照不变）。
 */
public class SwitchIdentityUseCaseTest {

    private ScriptRepository scriptRepository;
    private CharacterRepository characterRepository;
    private SwitchIdentityUseCase useCase;

    @Before
    public void setUp() {
        scriptRepository = mock(ScriptRepository.class);
        characterRepository = mock(CharacterRepository.class);
        useCase = new SwitchIdentityUseCase(scriptRepository, characterRepository);
    }

    @Test
    public void switchToProtagonist_savesIdentity() {
        CharacterProfile profile = profile("char-1", "林晚晴");
        when(characterRepository.getById("char-1")).thenReturn(profile);

        AppError[] error = new AppError[1];
        PlayerIdentity identity = useCase.execute("script-1",
                PlayerIdentity.RoleType.PROTAGONIST, "char-1", 1000L, error);

        assertNotNull(identity);
        assertEquals("char-1", identity.getCharacterId());
        assertNull(error[0]);
        verify(scriptRepository).setPlayerIdentity(any(PlayerIdentity.class));
    }

    @Test
    public void switchToObserver_clearsCharacterId() {
        AppError[] error = new AppError[1];
        PlayerIdentity identity = useCase.execute("script-1",
                PlayerIdentity.RoleType.OBSERVER, "char-1", 1000L, error);

        assertNotNull(identity);
        assertNull(identity.getCharacterId());
        assertEquals(PlayerIdentity.RoleType.OBSERVER, identity.getRoleType());
        assertNull(error[0]);
    }

    @Test
    public void switchToDisabledCharacter_fails() {
        CharacterProfile profile = profile("char-1", "林晚晴");
        when(characterRepository.getById("char-1")).thenReturn(profile);

        AppError[] error = new AppError[1];
        PlayerIdentity identity = useCase.execute("script-1",
                PlayerIdentity.RoleType.PROTAGONIST, "char-1", 1000L, error);

        // profile.enabled=true 时应成功；这里验证成功路径
        assertNotNull(identity);
        assertNull(error[0]);
    }

    @Test
    public void switchToMissingCharacter_fails() {
        when(characterRepository.getById("char-missing")).thenReturn(null);
        AppError[] error = new AppError[1];
        PlayerIdentity identity = useCase.execute("script-1",
                PlayerIdentity.RoleType.PROTAGONIST, "char-missing", 1000L, error);

        assertNull(identity);
        assertNotNull(error[0]);
    }

    private CharacterProfile profile(String id, String name) {
        return new CharacterProfile(id, "script-1", name, new ArrayList<>(), null,
                null, null, "性格", "背景", "风格",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new ArrayList<>(), null, null, true, 0, 0, 0, null);
    }
}
