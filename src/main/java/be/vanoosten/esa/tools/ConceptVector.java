/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.vanoosten.esa.tools;

import be.vanoosten.esa.WikiIndexer;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author user
 */
public class ConceptVector {

    Map<String, Float> conceptWeights;

    ConceptVector(TopDocs td, IndexReader indexReader) throws IOException {
        double norm = 0.0;
        conceptWeights = new HashMap<>();
        for (ScoreDoc scoreDoc : td.scoreDocs) {
            norm += scoreDoc.score * scoreDoc.score;
            String concept = indexReader.document(scoreDoc.doc).get(WikiIndexer.TITLE_FIELD);
            conceptWeights.put(concept, scoreDoc.score);
        }
        for (String concept : conceptWeights.keySet()) {
            conceptWeights.put(concept, (float) (conceptWeights.get(concept) / norm));
        }
    }

    public float dotProduct(ConceptVector other) {
        Set<String> commonConcepts = new HashSet<>(other.conceptWeights.keySet());
        commonConcepts.retainAll(conceptWeights.keySet());
        double dotProd = 0.0;
        for (String concept : commonConcepts) {
            dotProd += conceptWeights.get(concept) * other.conceptWeights.get(concept);
        }
        return (float) dotProd;
    }

    public Iterator<String> topConcepts(int n) {
        return conceptWeights.entrySet().stream().
                sorted((Map.Entry<String, Float> e1, Map.Entry<String, Float> e2) -> (int) Math.signum(e1.getValue() - e2.getValue())).
                map(e -> e.getKey()).
                iterator();
    }
}
