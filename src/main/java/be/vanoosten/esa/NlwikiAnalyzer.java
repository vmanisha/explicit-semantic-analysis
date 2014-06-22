package be.vanoosten.esa;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter.StemmerOverrideMap;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArrayMap;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;

// copied from DutchAnalyzer, changed StandardTokenizer to WikipediaTokenizer
public final class NlwikiAnalyzer extends Analyzer {
  
  /** File containing default Dutch stopwords. */
  public final static String DEFAULT_STOPWORD_FILE = "dutch_stop.txt";

  /**
   * Returns an unmodifiable instance of the default stop-words set.
   * @return an unmodifiable instance of the default stop-words set.
   */
  public static CharArraySet getDefaultStopSet(){
    return DefaultSetHolder.DEFAULT_STOP_SET;
  }
  
  private static class DefaultSetHolder {
    static final CharArraySet DEFAULT_STOP_SET;
    static final CharArrayMap<String> DEFAULT_STEM_DICT;
    static {
      try {
        DEFAULT_STOP_SET = WordlistLoader.getSnowballWordSet(IOUtils.getDecodingReader(SnowballFilter.class, 
            DEFAULT_STOPWORD_FILE, StandardCharsets.UTF_8), Version.LUCENE_CURRENT);
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stopword set");
      }
      
      DEFAULT_STEM_DICT = new CharArrayMap<>(Version.LUCENE_CURRENT, 4, false);
      DEFAULT_STEM_DICT.put("fiets", "fiets"); //otherwise fiet
      DEFAULT_STEM_DICT.put("bromfiets", "bromfiets"); //otherwise bromfiet
      DEFAULT_STEM_DICT.put("ei", "eier");
      DEFAULT_STEM_DICT.put("kind", "kinder");
    }
  }


  /**
   * Contains the stopwords used with the StopFilter.
   */
  private final CharArraySet stoptable;

  /**
   * Contains words that should be indexed but not stemmed.
   */
  private CharArraySet excltable = CharArraySet.EMPTY_SET;

  private final StemmerOverrideMap stemdict;

  // null if on 3.1 or later - only for bw compat
  private final CharArrayMap<String> origStemdict;
  private final Version matchVersion;

  public NlwikiAnalyzer(){
      this(Version.LUCENE_48);
  }
  
  /**
   * Builds an analyzer with the default stop words ({@link #getDefaultStopSet()}) 
   * and a few default entries for the stem exclusion table.
   * 
   */
  public NlwikiAnalyzer(Version matchVersion) {
    // historically, only this ctor populated the stem dict!!!!!
    this(matchVersion, DefaultSetHolder.DEFAULT_STOP_SET, CharArraySet.EMPTY_SET, DefaultSetHolder.DEFAULT_STEM_DICT);
  }
  
  public NlwikiAnalyzer(Version matchVersion, CharArraySet stopwords){
    // historically, this ctor never the stem dict!!!!!
    // so we populate it only for >= 3.6
    this(matchVersion, stopwords, CharArraySet.EMPTY_SET, 
        matchVersion.onOrAfter(Version.LUCENE_36) 
        ? DefaultSetHolder.DEFAULT_STEM_DICT 
        : CharArrayMap.<String>emptyMap());
  }
  
  public NlwikiAnalyzer(Version matchVersion, CharArraySet stopwords, CharArraySet stemExclusionTable){
    // historically, this ctor never the stem dict!!!!!
    // so we populate it only for >= 3.6
    this(matchVersion, stopwords, stemExclusionTable,
        matchVersion.onOrAfter(Version.LUCENE_36)
        ? DefaultSetHolder.DEFAULT_STEM_DICT
        : CharArrayMap.<String>emptyMap());
  }
  
  public NlwikiAnalyzer(Version matchVersion, CharArraySet stopwords, CharArraySet stemExclusionTable, CharArrayMap<String> stemOverrideDict) {
    this.matchVersion = matchVersion;
    this.stoptable = CharArraySet.unmodifiableSet(CharArraySet.copy(matchVersion, stopwords));
    this.excltable = CharArraySet.unmodifiableSet(CharArraySet.copy(matchVersion, stemExclusionTable));
    if (stemOverrideDict.isEmpty() || !matchVersion.onOrAfter(Version.LUCENE_31)) {
      this.stemdict = null;
      this.origStemdict = CharArrayMap.unmodifiableMap(CharArrayMap.copy(matchVersion, stemOverrideDict));
    } else {
      this.origStemdict = null;
      // we don't need to ignore case here since we lowercase in this analyzer anyway
      StemmerOverrideFilter.Builder builder = new StemmerOverrideFilter.Builder(false);
      CharArrayMap<String>.EntryIterator iter = stemOverrideDict.entrySet().iterator();
      CharsRef spare = new CharsRef();
      while (iter.hasNext()) {
        char[] nextKey = iter.nextKey();
        spare.copyChars(nextKey, 0, nextKey.length);
        builder.add(spare, iter.currentValue());
      }
      try {
        this.stemdict = builder.build();
      } catch (IOException ex) {
        throw new RuntimeException("can not build stem dict", ex);
      }
    }
  }
  
  /**
   * Returns a (possibly reused) {@link TokenStream} which tokenizes all the 
   * text in the provided {@link Reader}.
   *
   * @return A {@link TokenStream} built from a {@link StandardTokenizer}
   *   filtered with {@link StandardFilter}, {@link LowerCaseFilter}, 
   *   {@link StopFilter}, {@link SetKeywordMarkerFilter} if a stem exclusion set is provided,
   *   {@link StemmerOverrideFilter}, and {@link SnowballFilter}
   */
  @Override
  protected Analyzer.TokenStreamComponents createComponents(String fieldName, Reader aReader) {
      final Tokenizer source = new WikipediaTokenizer(aReader);
      TokenStream result = new StandardFilter(matchVersion, source);
      result = new LowerCaseFilter(matchVersion, result);
      result = new StopFilter(matchVersion, result, stoptable);
      if (!excltable.isEmpty())
        result = new SetKeywordMarkerFilter(result, excltable);
      if (stemdict != null)
        result = new StemmerOverrideFilter(result, stemdict);
      result = new SnowballFilter(result, new org.tartarus.snowball.ext.DutchStemmer());
      return new Analyzer.TokenStreamComponents(source, result);
  }
}

