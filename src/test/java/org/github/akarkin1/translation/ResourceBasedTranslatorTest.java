package org.github.akarkin1.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceBasedTranslatorTest {

    private final ResourceBasedTranslator translator = new ResourceBasedTranslator();

    @Test
    void translate_notAPaceHolder_noParams() {
      assertEquals("Not a placeholder",
                   translator.translate("en-US", "Not a placeholder"));
    }

    @Test
    void translate_notAPaceHolder_withParams() {
        assertEquals("Not a placeholder, value1=boo, value2=2",
                     translator.translate("en-US", "Not a placeholder, value1=%1$s, value2=%2$d", "boo", 2));
    }

    @Test
    void translate_notAPaceHolder_langNull() {
        assertEquals("Not a placeholder",
                     translator.translate(null, "Not a placeholder"));
    }

    @Test
    void translate_placeholder_langIsNull() {
        assertEquals("Default translation value – stringParameter=foo, numberParameter=12",
                     translator.translate(null, "${test.message.params}", 12, "foo"));

        assertEquals("Default translation value",
                     translator.translate(null, "${test.message.no-params}"));
    }

    @Test
    void translate_placeholder_langIsRU_ru() {
        assertEquals("Проверка перевода с параметрами: строковый=foo, числовой=12",
                     translator.translate("ru-RU", "${test.message.params}", 12, "foo"));

        assertEquals("Проверка перевода",
                     translator.translate("ru-RU", "${test.message.no-params}"));
    }

    @Test
    void translate_placeholder_langIsRu() {
        assertEquals("Проверка перевода с параметрами: строковый=foo, числовой=12",
                     translator.translate("ru", "${test.message.params}", 12, "foo"));

        assertEquals("Проверка перевода",
                     translator.translate("ru", "${test.message.no-params}"));
    }

    @Test
    void translate_placeholder_langIs_enUs() {
        assertEquals("American English translation value – stringParameter=foo, numberParameter=12",
                     translator.translate("en-US", "${test.message.params}", 12, "foo"));

        assertEquals("American English translation value",
                     translator.translate("en-US", "${test.message.no-params}"));
    }

    @Test
    void translate_placeholder_langIsRU_ru_butNoKeyFoundThere() {
        assertEquals("The value available only in English",
                     translator.translate("ru-RU", "${text.message.default.value}"));
    }

    @Test
    void translate_multiple_placeholders_langIsRu_ru_repeatedValue() {
        String messageWithPlaceholders =
            "Number 112 is translated to Russian as: "
            + "'${test.message.no-params.value3} ${test.message.params.value1}-${test.message.params.value1}-${test.message.params.value2}'";

        String expectedTranslation =
            "Number 112 is translated to Russian as: 'число один(1)-один(1)-два(2)'";

        assertEquals(expectedTranslation,
                     translator.translate("ru-RU", messageWithPlaceholders, 1, 2));
    }

    @Test
    void test_multilineSupport() {
      String expectedText = """
          Some enumeration:
           (1) first item;
           (2) second item.""";

      assertEquals(expectedText,
                   translator.translate("en-US", "${test.message.multiline}", 1, 2));
    }
}