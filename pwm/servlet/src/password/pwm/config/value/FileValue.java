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
import com.google.gson.GsonBuilder;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.SecureHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class FileValue extends AbstractValue implements StoredValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(FileValue.class);

    private Map<FileInformation, FileContent> values = new LinkedHashMap<>();

    public static class FileInformation {
        private String filename;
        private String filetype;

        public FileInformation(
                String filename,
                String filetype
        )
        {
            this.filename = filename;
            this.filetype = filetype;
        }

        public String getFilename()
        {
            return filename;
        }

        public String getFiletype()
        {
            return filetype;
        }
    }

    public static class FileContent {
        private byte[] contents;

        public FileContent(byte[] contents)
        {
            this.contents = contents;
        }

        public byte[] getContents()
        {
            return contents;
        }

        public static FileContent fromEncodedString(String input)
                throws IOException
        {
            byte[] convertedBytes = StringUtil.base64Decode(input);
            return new FileContent(convertedBytes);
        }

        public String toEncodedString()
                throws IOException
        {
            return StringUtil.base64Encode(contents, StringUtil.Base64Options.GZIP);
        }

        public String md5sum()
                throws PwmUnrecoverableException
        {
            return SecureHelper.md5sum(new ByteArrayInputStream(contents));
        }

        public String sha1sum()
                throws PwmUnrecoverableException
        {
            return SecureHelper.hash(new ByteArrayInputStream(contents), SecureHelper.HashAlgorithm.SHA1);
        }

        public int size()
        {
            return contents.length;
        }
    }

    public FileValue(Map<FileInformation, FileContent> values)
    {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {

            public FileValue fromXmlElement(Element settingElement, final String input)
                    throws PwmOperationalException
            {
                final Gson gson = JsonUtil.getGson();
                final List valueElements = settingElement.getChildren("value");
                final Map<FileInformation, FileContent> values = new LinkedHashMap<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;

                    final Element loopFileInformation = loopValueElement.getChild("FileInformation");
                    if (loopFileInformation != null) {
                        final String loopFileInformationJson = loopFileInformation.getText();
                        final FileInformation fileInformation = gson.fromJson(loopFileInformationJson,
                                FileInformation.class);

                        final Element loopFileContentElement = loopValueElement.getChild("FileContent");
                        if (loopFileContentElement != null) {
                            final String fileContentString = loopFileContentElement.getText();
                            final FileContent fileContent;
                            try {
                                fileContent = FileContent.fromEncodedString(fileContentString);
                                values.put(fileInformation, fileContent);
                            } catch (IOException e) {
                                LOGGER.error("error reading file contents item: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return new FileValue(values);
            }

            public StoredValue fromJson(String input)
            {
                throw new IllegalStateException("not implemented");
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName)
    {
        final List<Element> returnList = new ArrayList<>();
        final Gson gson = JsonUtil.getGson();
        for (final FileInformation fileInformation : values.keySet()) {
            final Element valueElement = new Element(valueElementName);

            final Element fileInformationElement = new Element("FileInformation");
            fileInformationElement.addContent(gson.toJson(fileInformation));
            valueElement.addContent(fileInformationElement);

            final Element fileContentElement = new Element("FileContent");
            final FileContent fileContent = values.get(fileInformation);
            try {
                fileContentElement.addContent(fileContent.toEncodedString());
            } catch (IOException e) {
                LOGGER.error("unexpected error writing setting to xml, IO error during base64 encoding: " + e.getMessage());
            }
            valueElement.addContent(fileContentElement);

            returnList.add(valueElement);
        }
        return returnList;
    }

    @Override
    public Object toNativeObject()
    {
        return values;
    }

    @Override
    public List<String> validateValue(PwmSetting pwm)
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString(
            boolean prettyFormat,
            Locale locale
    )
    {
        final Gson gson = prettyFormat
                ? JsonUtil.getGson(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping())
                : JsonUtil.getGson(new GsonBuilder().disableHtmlEscaping());

        final List<Map<String, Object>> output = asMetaData();
        return gson.toJson(output);
    }

    public List<Map<String, Object>> asMetaData()
    {
        final List<Map<String, Object>> output = new ArrayList<>();
        for (final FileInformation fileInformation : values.keySet()) {
            final FileContent fileContent = values.get(fileInformation);
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", fileInformation.getFilename());
            details.put("type", fileInformation.getFiletype());
            details.put("size", fileContent.size());
            try {
                details.put("md5sum", fileContent.md5sum());
            } catch (PwmUnrecoverableException e) { /* noop */ }
            output.add(details);
        }
        return output;
    }

    public List<Map<String,Object>> toInfoMap() {
        if (values == null) {
            return Collections.emptyList();
        }
        final List<Map<String,Object>> returnObj = new ArrayList<>();
        for (FileValue.FileInformation fileInformation : this.values.keySet()) {
            final FileContent fileContent = this.values.get(fileInformation);
            final LinkedHashMap<String, Object> loopInfo = new LinkedHashMap<>();
            loopInfo.put("name", fileInformation.getFilename());
            loopInfo.put("type", fileInformation.getFiletype());
            loopInfo.put("size", fileContent.size());
            try {
                loopInfo.put("md5sum", fileContent.md5sum());
                loopInfo.put("sha1sum", fileContent.sha1sum());
            } catch (PwmUnrecoverableException e) {
                LOGGER.warn("error generating hash for certificate: " + e.getMessage());
            }
            returnObj.add(loopInfo);
        }
        return Collections.unmodifiableList(returnObj);
    }
}
