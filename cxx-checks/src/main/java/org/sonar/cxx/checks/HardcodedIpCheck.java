/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2011 Waleri Enns and CONTACT Software GmbH
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.cxx.checks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import org.sonar.api.utils.SonarException;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.squid.checks.SquidCheck;

import org.sonar.cxx.parser.CxxGrammarImpl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Rule(
  key = "NoHardcodedIpCheck",
  description = "IP addresses should never be hardcoded into the source code",
  priority = Priority.CRITICAL)

public class HardcodedIpCheck extends SquidCheck<Grammar>  {

// full IPv6:
//  (^\d{20}$)|(^((:[a-fA-F0-9]{1,4}){6}|::)ffff:(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[0-9]{1,2})(\.(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[0-9]{1,2})){3}$)|(^((:[a-fA-F0-9]{1,4}){6}|::)ffff(:[a-fA-F0-9]{1,4}){2}$)|(^([a-fA-F0-9]{1,4}) (:[a-fA-F0-9]{1,4}){7}$)|(^:(:[a-fA-F0-9]{1,4}(::)?){1,6}$)|(^((::)?[a-fA-F0-9]{1,4}:){1,6}:$)|(^::$)
// simple IPV4 and IPV6 address:
//  ([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\d{1,3}\.){3}\d{1,3}
// IPv4 with port number
//  (?:^|\s)([a-z]{3,6}(?=://))?(://)?((?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.(?:25[0-5]|2[0-4]\d|[01]?\d\d?))(?::(\d{2,5}))?(?:\s|$)
// original patter for IPv4:
//  [^\d.]*?((?:\d{1,3}\.){3}\d{1,3}(?!\d|\.)).*?

 
  private final Map<String, Integer> firstOccurrence = Maps.newHashMap();
  private final Map<String, Integer> IPOccurrences = Maps.newHashMap();
  private static final String DEFAULT_REGULAR_EXPRESSION = "[^\\d.]*?((?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d|\\.)).*?";
  private static Matcher IP = null;
  
  @RuleProperty(
      key = "regularExpression",
      defaultValue = "" + DEFAULT_REGULAR_EXPRESSION)
    public String regularExpression = DEFAULT_REGULAR_EXPRESSION;
  
  public String getRegularExpression() {
    return regularExpression;
  }
  
  @Override
  public void init() {
    String regularExpression = getRegularExpression();
    checkNotNull(regularExpression, "getRegularExpression() should not return null");

    if (!Strings.isNullOrEmpty(regularExpression)) {
      try {    
       IP = Pattern.compile(getRegularExpression()).matcher("");
      } catch (RuntimeException e) {
        throw new SonarException("Unable to compile regular expression: " + regularExpression, e);
      }
    }
    subscribeTo(CxxGrammarImpl.LITERAL);  
  }

  @Override
  public void visitNode(AstNode node) {
      visitOccurence(node.getTokenOriginalValue(), node.getTokenLine());
  }
  
  @Override
  public void leaveFile(AstNode node) {
    for (Map.Entry<String, Integer> literalOccurences : IPOccurrences.entrySet()) {
      Integer occurences = literalOccurences.getValue();
      String ip = literalOccurences.getKey();
      getContext().createLineViolation(this, "Make this IP \"" + ip + "\" address configurable (occurs " + occurences + " times).", firstOccurrence.get(ip));
    }
  }
  
  private void visitOccurence(String literal, int line) {
       IP.reset(literal);
       if (IP.find()) {
          String ip = IP.group(0); 
          if (literal.startsWith(ip)) {
            literal = ip.replaceFirst("\"", "");
        
            if (!firstOccurrence.containsKey(literal)) {
              firstOccurrence.put(literal, line);
              IPOccurrences.put(literal, 1);
            } else {
              int occurences = IPOccurrences.get(literal);
              IPOccurrences.put(literal, occurences + 1);
            }
          }
        }
      }
}
