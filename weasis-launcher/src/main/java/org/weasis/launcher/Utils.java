/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Utils {

  private Utils() {}

  public static boolean getEmptytoFalse(String val) {
    if (hasText(val)) {
      return getBoolean(val);
    }
    return false;
  }

  public static boolean geEmptytoTrue(String val) {
    if (hasText(val)) {
      return getBoolean(val);
    }
    return true;
  }

  private static boolean getBoolean(String val) {
    return Boolean.TRUE.toString().equalsIgnoreCase(val);
  }

  public static boolean hasLength(CharSequence str) {
    return str != null && str.length() > 0;
  }

  public static boolean hasLength(String str) {
    return hasLength((CharSequence) str);
  }

  public static boolean hasText(String str) {
    // remove all the white spaces and then trim then compute its length
    // I tested the code accuracy, the result is that in 98.2% of tests it took
    // the same duration, and the rest took maximum 2 milliseconds duration than yours
    // implementation
    if (str == null) {
      return false;
    }

    String whiteSpaceRegex = "\\s";
    String emptyString = "";
    return str.replaceAll(whiteSpaceRegex, emptyString).trim().length() > 0;
  }

  public static String getWeasisProtocol(String... params) {
    Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?");
    for (String p : params) {
      if (pattern.matcher(p).matches()) {
        return p;
      }
    }
    return null;
  }

  public static int getWeasisProtocolIndex(String... params) {
    Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?");
    for (int i = 0; i < params.length; i++) {
      if (pattern.matcher(params[i]).matches()) {
        return i;
      }
    }
    return -1;
  }

  public static String removeEnglobingQuotes(String value) {
    return value.replaceAll("(?:^\")|(?:\"$)", "");
  }

  public static String adaptPathToUri(String value) {
    return value.replace("\\", "/").replace(" ", "%20");
  }

  public static List<String> splitSpaceExceptInQuotes(String s) {
    if (s == null) {
      return Collections.emptyList();
    }
    List<String> matchList = new ArrayList<>();
    Pattern patternSpaceExceptQuotes = Pattern.compile("'[^']*'|\"[^\"]*\"|( )");
    Matcher m = patternSpaceExceptQuotes.matcher(s);
    StringBuffer b = new StringBuffer();
    while (m.find()) {
      if (m.group(1) == null) {
        m.appendReplacement(b, m.group(0));
        String arg = b.toString();
        b.setLength(0);
        if (Utils.hasText(arg)) {
          matchList.add(arg.trim());
        }
      }
    }
    b.setLength(0);
    m.appendTail(b);
    String arg = b.toString();
    if (Utils.hasText(arg)) {
      matchList.add(arg.trim());
    }
    return matchList;
  }

  public static byte[] getByteArrayProperty(Properties prop, String key, byte[] def) {
    byte[] result = def;
    if (key != null) {
      String value = prop.getProperty(key);
      if (Utils.hasText(value)) {
        try {
          result = FileUtil.gzipUncompressToByte(Base64.getDecoder().decode(value.getBytes()));
        } catch (IOException e) {
          Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, "Get byte property", e);
        }
      }
    }
    return result;
  }

  public static byte[] decrypt(byte[] input, String strKey) throws GeneralSecurityException {
    SecretKeySpec skeyspec =
        new SecretKeySpec(Objects.requireNonNull(strKey).getBytes(), "Blowfish"); // NON-NLS
    Cipher cipher = Cipher.getInstance("Blowfish"); // NON-NLS
    cipher.init(Cipher.DECRYPT_MODE, skeyspec);
    return cipher.doFinal(input);
  }
}
