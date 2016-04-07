/*
JJSP - Java and Javascript Server Pages 
Copyright (C) 2016 Global Travel Ventures Ltd

This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by 
the Free Software Foundation, either version 3 of the License, or 
(at your option) any later version.

This program is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
for more details.

You should have received a copy of the GNU General Public License along with 
this program. If not, see http://www.gnu.org/licenses/.
*/
package jjsp.util;

import java.util.*;

public class TextMatcher
{
    public static class TextMatch
    {
        private int total;
        private int[] frequencies;
        private String[] allWords;

        TextMatch(String[] words, int[] freqencies)
        {
            this.allWords = words;
            this.frequencies = freqencies;

            total = 1;
            for (int i=0; i<freqencies.length; i++)
                total += freqencies[i];
        }

        public double getMatchScore(String term)
        {
            double closeScores = 0, farScores = 0;

            for (int i=0; i<allWords.length; i++)
            {
                int editDistance = editDistance(allWords[i], term);
                if (editDistance < 4)
                    closeScores += frequencies[i];
                else
                    farScores += frequencies[i];
            }
            
            return closeScores*(1 - farScores/total);
        }
        
        public double getMatchScore(String[] terms)
        { 
            double total = 0;
            for (int i=0; i<terms.length; i++)
                total += getMatchScore(terms[i]);
            return total;
        }
        
        public boolean ranksAbove(String[] terms, TextMatch other)
        {
            int aboveCount = 0;
            for (int i=0; i<terms.length; i++)
            {
                double score1 = getMatchScore(terms[i]);
                double score2 = other.getMatchScore(terms[i]);

                if (score1 > score2)
                    aboveCount++;
            }

            return aboveCount >= (terms.length+1)/2;
        }
    }

    public static String stripPunctuationAndExtraSpaces(String text)
    {
        StringBuffer buf = new StringBuffer();
        
        for (int i=0; i<text.length(); i++)
        {
            char ch = text.charAt(i);
            if (!Character.isLetterOrDigit(ch))
                ch = ' ';
            
            if (ch == ' ')
            {
                if ((buf.length() > 0) && (buf.charAt(buf.length()-1) == ' '))
                    continue;
            }

            buf.append(ch);
        }

        return buf.toString();
    }

    public static boolean isNumeric(String src)
    {
        for (int i=0; i<src.length(); i++)
            if (!Character.isDigit(src.charAt(i)))
                return false;
        return true;
    }

    public static boolean mixedNumericAndAlpha(String src)
    {
        int digits = 0;
        int alphas = 0;

        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);
            
            if (Character.isDigit(ch))
                digits++;
            else if (Character.isLetter(ch))
                alphas++;
        }

        return (digits>0) && (alphas>0);
    }

    public static boolean isCamelCase(String src)
    {
        if (src.length() == 1)
            return false;
        if (Character.isUpperCase(src.charAt(0)))
            return false;

        int upperCount = 0;
        for (int i=1; i<src.length(); i++)
        {
            char ch = src.charAt(i);
            if (Character.isUpperCase(ch))
                upperCount++;
            if (Character.isWhitespace(ch))
                return false;
        }
        
        return (upperCount > 0) && (upperCount < src.length()/2);
    }

    public static TextMatch createTextMatchFor(String sourceText)
    {
        String[] terms = sourceText.split(" ");

        TreeMap freqCount = new TreeMap();

        for (int i=0; i<terms.length; i++)
        {
            String term = terms[i].trim().toLowerCase();
            if (terms[i].length() <= 2)
                continue;

            Integer count = (Integer) freqCount.get(term);
            if (count == null)
                freqCount.put(term, Integer.valueOf(1));
            else
                freqCount.put(term, Integer.valueOf(count.intValue()+1));
        }

        String[] uniqueTerms = new String[freqCount.size()];
        int[] counts = new int[uniqueTerms.length];

        freqCount.keySet().toArray(uniqueTerms);
        for (int i=0; i<uniqueTerms.length; i++)
            counts[i] = ((Integer) freqCount.get(uniqueTerms[i])).intValue();
        
        return new TextMatch(uniqueTerms, counts);
    }

    public static int getClosestTermMatch(String[] searchTerms, String[] terms)
    {
        int total = 0;
        for (int i=0; i<searchTerms.length; i++)
        {
            String term = searchTerms[i];
            int min = 10000;

            for (int j=0; j<terms.length; j++)
            {
                int ed = editDistance(term, terms[j]);
                if (ed < min)
                    min = ed;
            }
            
            total += min;
        }

        return total;
    }

    public static void sortListWithSearchTerms(String[] sourceList, String[] searchTerms)
    {
        for (int i=0; i<sourceList.length; i++)
        {
            String[] terms = stripPunctuationAndExtraSpaces(sourceList[i]).split(" ");
            int score = getClosestTermMatch(searchTerms, terms);
            
            sourceList[i] = String.format("%04d", score)+" "+sourceList[i];
        }
        
        Arrays.sort(sourceList);
        
        for (int i=0; i<sourceList.length; i++)
            sourceList[i] = sourceList[i].substring(5);
    }

    public static int editDistance(String s1, String s2)
    {
        int[][] cachedResults = new int[s1.length()][s2.length()];
        return editDistance(cachedResults, s1, 0, s2, 0);
    }

    private static int editDistance(int[][] cachedResults, String s1, int pos1, String s2, int pos2)
    {
        if (pos1 == s1.length())
            return s2.length() - pos2;
        if (pos2 == s2.length())
            return s1.length() - pos1;

        if (cachedResults[pos1][pos2] > 0)
            return cachedResults[pos1][pos2] - 1;

        int r1 = editDistance(cachedResults, s1, pos1+1, s2, pos2) + 1;
        int r2 = editDistance(cachedResults, s1, pos1, s2, pos2+1) + 1;
        int r3 = editDistance(cachedResults, s1, pos1+1, s2, pos2+1);
        if (s1.charAt(pos1) != s2.charAt(pos2))
            r3 += 2;

        int result = Math.min(r1, Math.min(r2, r3));
        cachedResults[pos1][pos2] = result + 1;
        
        return result;
    }

    public static int longestCommonSubsequence(String s1, String s2)
    {
        int len1 = s1.length();
        int len2 = s2.length();
        
        if ((len1 == 0) || (len2 == 0))
            return 0;

        String ss1 = s1.substring(0, len1-1);
        String ss2 = s2.substring(0, len2-1);

        if (s1.charAt(len1-1) == s2.charAt(len2-1))
            return longestCommonSubsequence(ss1, ss2)+1;

        int r1 = longestCommonSubsequence(s1, ss2);
        int r2 = longestCommonSubsequence(ss1, s2);
        return Math.max(r1, r2);
    }

    public static void main(String[] args)
    {
        String[] list = new String[]{" hello ", "hello2 ", "ello jam", "jam jim"};
        
        sortListWithSearchTerms(list, new String[]{"jam", "llo"});
        
        for (int i=0; i<list.length; i++)
            System.out.println(list[i]);
    }
}
