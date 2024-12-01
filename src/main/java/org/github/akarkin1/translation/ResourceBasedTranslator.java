package org.github.akarkin1.translation;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceBasedTranslator implements Translator {

    private static final String DEFAULT_TRANSLATIONS_FILE = "messages.properties";
    private static final String LANG_TRANSLATIONS_FILE = "messages_%1$s.properties";
    private static final String DEFAULT_LANGUAGE_CODE = "en-US";
    private static final Pattern MESSAGE_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(.*?)}");

    private final Map<String, Properties> translationsCached = new HashMap<>();

    @Override
    public String translate(String langCode, String message, Object... params) {
        List<String> translationKeys = findTranslationKeys(message);
        if (translationKeys.isEmpty()) {
            return message.formatted(params);
        }

        String translatedMessage = StringUtils.EMPTY + message;

        for (String translationKey : translationKeys) {
            String translatedValue = translateValue(langCode, message, translationKey);
            translatedMessage = translatedMessage.replace("${%s}".formatted(translationKey), translatedValue);
        }

        return translatedMessage.formatted(params);
    }

    private String translateValue(String langCode, String message, String translationKey) {
        Properties translation = getCachedOrLoadTranslation(message, langCode);
        // fallback to the default file – the best we can do
        if (!translation.containsKey(translationKey)) {
            return getCachedOrLoadTranslation(message, StringUtils.EMPTY)
                .getProperty(translationKey, message);
        }

        return translation.getProperty(translationKey);
    }

    private static List<String> findTranslationKeys(String message) {
        List<String> foundKeys = new ArrayList<>();
        Matcher placeholderMatcher = MESSAGE_PLACEHOLDER_PATTERN.matcher(message);

        while(placeholderMatcher.find()) {
            foundKeys.add(placeholderMatcher.group(1));
        }

        return foundKeys
            .stream()
            .distinct()
            .toList();
    }

    private Properties getCachedOrLoadTranslation(String message, String langCode) {
        if (translationsCached.containsKey(langCode)) {
            return translationsCached.get(langCode);
        }

        try (InputStream resource = getTranslationsResource(langCode)) {
            Properties messages = new Properties();
            messages.load(resource);
            translationsCached.put(langCode, messages);
            return messages;
        } catch (IOException e) {
            throw new RuntimeException("Translation of the message failed: %s".formatted(message));
        }
    }

    private static InputStream getTranslationsResource(String langCode) {
        IetfCode ietfCode = IetfCode.fromString(langCode);
        return loadTranslationResource(ietfCode.fullCode())
            .or(() -> loadTranslationResource(ietfCode.tag()))
            .or(() -> loadTranslationResource(ietfCode.tag().toLowerCase()))
            .or(() -> loadTranslationResource(DEFAULT_LANGUAGE_CODE))
            .or(() -> loadTranslationResource(StringUtils.EMPTY))
            .orElseThrow(() -> new RuntimeException("No messages.properties file provided in the resources"));
    }

    private static Optional<InputStream> loadTranslationResource(String lang) {
        ClassLoader classLoader = ResourceBasedTranslator.class.getClassLoader();
        return Optional.ofNullable(classLoader.getResourceAsStream(translationResourceFor(lang)));
    }

    private static String translationResourceFor(String langCode) {
        if (StringUtils.isEmpty(langCode)) {
            return DEFAULT_TRANSLATIONS_FILE;
        }
        return LANG_TRANSLATIONS_FILE.formatted(langCode);
    }

    private record IetfCode(String tag, String subtag) {
        boolean hasSubtag() {
            return subtag != null;
        }

        String fullCode() {
            return hasSubtag() ? "%1$s-%2$s".formatted(tag, subtag) : tag;
        }

        static IetfCode fromString(String languageCode) {
            if (StringUtils.isBlank(languageCode)) {
                return new IetfCode(StringUtils.EMPTY, null);
            }

            String[] codeSplit = languageCode.split("-");

            if (codeSplit.length < 2) {
                return new IetfCode(languageCode, null);
            }

            return new IetfCode(codeSplit[0], codeSplit[1]);

        }
    }
}
