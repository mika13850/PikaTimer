package com.pikatimer.util;

import com.pikatimer.PikaPreferences;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Manages internationalization (i18n) for the application.
 * Provides methods to get translated strings and switch between languages.
 */
public class I18nManager {

  private static final String BUNDLE_NAME = "com.pikatimer.i18n.messages";
  private static I18nManager instance;

  private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.ENGLISH);
  private ResourceBundle resourceBundle;

  private I18nManager() {
    // Load saved locale from preferences
    String savedLocale = PikaPreferences.getInstance().getLocale();
    setLocaleByLanguage(savedLocale);
    
    this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale.get());

    // Listen for locale changes and update resource bundle
    locale.addListener((obs, oldVal, newVal) -> {
      this.resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, newVal);
      // Save locale to preferences when it changes
      PikaPreferences.getInstance().setLocale(newVal.getLanguage());
    });
  }

  /**
   * Gets the singleton instance of I18nManager
   */
  public static synchronized I18nManager getInstance() {
    if (instance == null) {
      instance = new I18nManager();
    }
    return instance;
  }

  /**
   * Gets a translated string for the given key
   * @param key the resource key
   * @return the translated string, or the key if not found
   */
  public String getString(String key) {
    try {
      return resourceBundle.getString(key);
    } catch (Exception e) {
      return key;
    }
  }

  /**
   * Gets a translated string with parameters
   * @param key the resource key
   * @param params the parameters to substitute
   * @return the translated string with parameters substituted
   */
  public String getString(String key, Object... params) {
    try {
      String template = resourceBundle.getString(key);
      return String.format(template, params);
    } catch (Exception e) {
      return key;
    }
  }

  /**
   * Sets the current locale
   * @param locale the new locale
   */
  public void setLocale(Locale locale) {
    this.locale.set(locale);
  }

  /**
   * Gets the current locale
   * @return the current locale
   */
  public Locale getLocale() {
    return locale.get();
  }

  /**
   * Gets the locale property for binding
   * @return the locale property
   */
  public ObjectProperty<Locale> localeProperty() {
    return locale;
  }

  /**
   * Sets the locale by language code
   * @param languageCode the language code (e.g., "en", "fr")
   */
  public void setLocaleByLanguage(String languageCode) {
    switch (languageCode.toLowerCase()) {
      case "fr":
      case "fr_fr":
        setLocale(Locale.FRENCH);
        break;
      case "en":
      case "en_us":
      default:
        setLocale(Locale.ENGLISH);
        break;
    }
  }

  /**
   * Gets the current language code
   * @return the language code
   */
  public String getLanguageCode() {
    return locale.get().getLanguage();
  }

  /**
   * Gets the current ResourceBundle for use with FXMLLoader
   * @return the current resource bundle
   */
  public ResourceBundle getResourceBundle() {
    return resourceBundle;
  }
}
