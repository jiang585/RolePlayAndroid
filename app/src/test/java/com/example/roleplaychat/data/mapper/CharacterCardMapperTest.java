package com.example.roleplaychat.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.util.JsonUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * 角色卡映射测试（架构文档 §15.2-12：导出再导入后角色映射与引用一致；
 * §9.2：默认导出不含隐藏设定）。
 */
public class CharacterCardMapperTest {

    @Test
    public void toEnvelope_defaultExcludesHiddenSetting() {
        CharacterProfile profile = new CharacterProfile("char-1", "script-1", "林晚晴",
                new ArrayList<>(Arrays.asList("晚晚")), "avatars/a.webp", "女", "21",
                "温和", "旧书铺长大", "简洁",
                new ArrayList<>(Arrays.asList("先别急")), new ArrayList<>(Arrays.asList("观察力强")),
                new ArrayList<>(), new LinkedHashMap<>(), new ArrayList<>(Arrays.asList("你好")),
                null, "秘密设定", true, 0, 0, 0, null);

        CharacterCardMapper.Envelope envelope = CharacterCardMapper.toEnvelope(profile, false);
        assertNull(envelope.data.hiddenSetting);
        assertEquals("林晚晴", envelope.data.name);
        assertEquals("女", envelope.data.gender);

        // includeHidden=true 时写入
        CharacterCardMapper.Envelope withHidden = CharacterCardMapper.toEnvelope(profile, true);
        assertEquals("秘密设定", withHidden.data.hiddenSetting);
    }

    @Test
    public void roundTrip_preservesFields() {
        CharacterProfile profile = new CharacterProfile("char-1", "script-1", "张三",
                new ArrayList<>(Arrays.asList("三哥")), null, "男", "30",
                "豪爽", "镖师", "大嗓门",
                new ArrayList<>(Arrays.asList("痛快！")), new ArrayList<>(Arrays.asList("力大")),
                new ArrayList<>(Arrays.asList("冲动")), new LinkedHashMap<>(),
                new ArrayList<>(Arrays.asList("来，喝一杯")), null, null, true, 1, 0, 0, null);

        CharacterCardMapper.Envelope envelope = CharacterCardMapper.toEnvelope(profile, false);
        String json = JsonUtils.toJson(envelope);
        CharacterCardMapper.Envelope parsed = JsonUtils.fromJson(json, CharacterCardMapper.Envelope.class);
        assertNotNull(parsed);
        assertEquals("张三", parsed.data.name);
        assertEquals("30", parsed.data.age);
        assertEquals("痛快！", parsed.data.catchphrases.get(0));
    }
}
