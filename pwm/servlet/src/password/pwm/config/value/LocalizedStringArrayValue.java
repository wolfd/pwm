/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.value;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.JsonUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalizedStringArrayValue extends AbstractValue implements StoredValue {
    final Map<String, List<String>> values;

    LocalizedStringArrayValue(final Map<String, List<String>> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public LocalizedStringArrayValue fromJson(final String input)
            {
                if (input == null) {
                    return new LocalizedStringArrayValue(Collections.<String, List<String>>emptyMap());
                } else {
                    final Gson gson = JsonUtil.getGson();
                    Map<String, List<String>> srcMap = gson.fromJson(input, new TypeToken<Map<String, List<String>>>() {
                    }.getType());
                    srcMap = srcMap == null ? Collections.<String, List<String>>emptyMap() : new TreeMap<>(srcMap);
                    return new LocalizedStringArrayValue(Collections.unmodifiableMap(srcMap));
                }
            }

            public LocalizedStringArrayValue fromXmlElement(final Element settingElement, final String key)
            {
                final List valueElements = settingElement.getChildren("value");
                final Map<String, List<String>> values = new TreeMap<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String localeString = loopValueElement.getAttributeValue(
                            "locale") == null ? "" : loopValueElement.getAttributeValue("locale");
                    final String value = loopValueElement.getText();
                    List<String> valueList = values.get(localeString);
                    if (valueList == null) {
                        valueList = new ArrayList<>();
                        values.put(localeString, valueList);
                    }
                    valueList.add(value);
                }
                return new LocalizedStringArrayValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String locale : values.keySet()) {
            for (final String value : values.get(locale)) {
                final Element valueElement = new Element(valueElementName);
                valueElement.addContent(new CDATA(value));
                if (locale != null && locale.length() > 0) {
                    valueElement.setAttribute("locale", locale);
                }
                returnList.add(valueElement);
            }
        }
        return returnList;
    }

    public Map<String, List<String>> toNativeObject() {
        return Collections.unmodifiableMap(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.keySet().iterator().next().length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for (final String locale : values.keySet()) {
            for (final String loopValue : values.get(locale)) {
                if (loopValue != null && loopValue.length() > 0) {
                    final Matcher matcher = pattern.matcher(loopValue);
                    if (!matcher.matches()) {
                        return Collections.singletonList("incorrect value format for value '" + loopValue + "'");
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(boolean prettyFormat, Locale locale) {
        if (prettyFormat && values != null && !values.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final String localeKey : values.keySet()) {
                if (!values.get(localeKey).isEmpty()) {
                    sb.append("Locale: ").append(LocaleHelper.debugLabel(LocaleHelper.parseLocaleString(localeKey))).append("\n");
                    for (final String value : values.get(localeKey)) {
                        sb.append("  ").append(value).append("\n");
                    }
                }
            }
            return sb.toString();
        } else {
            return JsonUtil.serializeMap(values);
        }
    }

}
