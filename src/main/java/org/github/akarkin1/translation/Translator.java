package org.github.akarkin1.translation;

public interface Translator {

    String translate(String langCode, String message, Object ...params);

}