/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package optimization;

import edu.stanford.nlp.classify.ColumnDataClassifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import gmm.GMMDiag;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import linearclassifier.AnalyzeLClassifier;
import linearclassifier.Margin;
import linearclassifier.NumericalIntegration;
import tools.CNConstants;
import tools.Histoplot;
import tools.PlotAPI;


/**
 *
 * @author rojasbar
 */
public class MultiCoreStocCoordDescent  {

  private MulticoreWrapper<Pair<Integer, Margin>, Pair<Integer, Double>> wrapper;
  private ThreadsafeProcessor<Pair<Integer,Margin>,Pair<Integer,Double>> coordGradThr;
  private int nThreads;
  
  
  public MultiCoreStocCoordDescent(int niters, int nThreads, boolean isclose, boolean ismontecarlo, int numniiters){
      this.nThreads = nThreads;
      coordGradThr = new CoordinateGradThreadProcessor(niters, isclose,ismontecarlo,numniiters);
      wrapper = new MulticoreWrapper(nThreads,coordGradThr);
  }
  

  public int getNThreads(){
      return this.nThreads;
  }
  
  public MulticoreWrapper<Pair<Integer, Margin>, Pair<Integer, Double>> getWrapper(){
      return this.wrapper;
  }
  
  /**
   * Implements the thread processor for coordinate gradient descent
   */
  private class CoordinateGradThreadProcessor implements ThreadsafeProcessor<Pair<Integer,Margin>,Pair<Integer,Double>> {

      private Random rnd = new Random();
      private boolean isCloseForm=true;
      private boolean isMonteCarloNI=false;
      private int niters=100;
      private int numIterNumIntegr=100;

    public CoordinateGradThreadProcessor(){

    }
    
    public CoordinateGradThreadProcessor(int niters,boolean isclose, boolean ismontecarlo, int numniiters){
        this.isCloseForm=isclose;
        this.isMonteCarloNI= ismontecarlo;
        this.numIterNumIntegr=numniiters;
        this.niters=niters;

    }
    

        
    
    /**
     * Le GMM modélise les scores de la class 0, i.e: (mu_0,0 ; sigma_0,0) et (mu_1,0 ; sigma_1,0)
     */
    float computeR(GMMDiag gmm, final float[] py, boolean isconstrained) {
        final float sqrtpi = (float)Math.sqrt(Math.PI);
        final float pi = (float)Math.PI;
        final float sigma00 = (float)Math.sqrt(gmm.getVar(0, 0, 0));
        
        final float sigma10 = (float)Math.sqrt(gmm.getVar(1, 0, 0));
        
        final float var00 = (float)gmm.getVar(0, 0, 0);
        final float var10 = (float)gmm.getVar(1, 0, 0);
        final float mean00  = (float)gmm.getMean(0, 0);
        final float mean01  = (float)gmm.getMean(0, 1);
        final float mean10  = (float)gmm.getMean(1, 0);
        final float mean11  = (float)gmm.getMean(1, 1);
        

        
        if(isconstrained){
            float t1 = py[0]*(1f-2f*mean00)/(4f*sigma00*sqrtpi) * (1f+(float)AnalyzeLClassifier.erf( (0.5-mean00)/sigma00 ));
            float t2 = py[0]/(2f*pi) * (float)Math.exp( -(0.5f-mean00)*(0.5f-mean00)/var00 );
            float t3 = py[1]*(1f+2f*mean10)/(4f*sigma10*sqrtpi) * (1f-(float)AnalyzeLClassifier.erf( (-0.5-mean10)/sigma10 ));
            float t4 = py[1]/(2f*pi) * (float)Math.exp( -(-0.5f-mean10)*(-0.5f-mean10)/var10 );
            return t1+t2+t3+t4;
        }
        
        float t1= 0.5f + (py[0]*mean01)/2f + (py[1]*mean10)/2f;
        float newsigma=  ((float) gmm.getVar(0, 0, 0) + (float)gmm.getVar(0, 1, 1));
        float t2 = py[0]*((float)gmm.getVar(0, 1, 1))* (float) gmm.getProbability(mean00, mean01+1, newsigma);
        newsigma=  ((float) gmm.getVar(1, 1, 1) + (float)gmm.getVar(1, 0, 0));
        float t3 = py[1]*((float)gmm.getVar(1, 0, 0))* (float) gmm.getProbability(mean11, mean10+1, newsigma);
        newsigma=  ((float) gmm.getVar(0, 1, 1) + (float)gmm.getVar(0, 0, 0));
        float t4= (py[0]/2f)*(mean00 - mean01 - 1)*(float)AnalyzeLClassifier.erf((mean00-mean01-1)/Math.sqrt(2*newsigma));
        newsigma=  ((float) gmm.getVar(1, 0, 0) + (float)gmm.getVar(1, 1, 1));
        float t5= (py[1]/2f)*(mean11 - mean10 - 1)*(float)AnalyzeLClassifier.erf((mean11-mean10-1)/Math.sqrt(2*newsigma));
                            
        
        return t1+t2+t3+t4+t5;
    }    
    
      
    
 
    
    public float computeROfTheta(Margin margin) {
        
        //final float[] priors = computePriors(sclassifier,model);
        final float[] priors = AnalyzeLClassifier.getPriors();
        // get scores
        GMMDiag gmm = new GMMDiag(priors.length, priors);
        gmm.train(margin);
        /*
        System.out.println("mean=[ "+gmm.getMean(0, 0)+" , "+gmm.getMean(0, 1)+";\n"+
        +gmm.getMean(1, 0)+" , "+gmm.getMean(1, 1)+"]");
        System.out.println("sigma=[ "+gmm.getVar(0, 0, 0)+" , "+gmm.getVar(0, 1, 1)+";\n"+
        +gmm.getVar(1, 0, 0)+" , "+gmm.getVar(1, 1, 1));
        System.out.println("GMM trained");
        */
        //return computeR(gmm, priors,marginMAP.get(sclassifier).getNlabs() );
        
        float r= computeR(gmm, priors,true); //xtof
                
        return r;
        
    }
    
    float computeRNumInt(GMMDiag gmm, final float[] py, int nLabels, boolean isMC, int numIters) {
        float risk=0f;
        
        NumericalIntegration mcInt = new NumericalIntegration();
        //double[] mvIntegral= mcInt.integrate(gmm, CNConstants.UNIFORM, true);
        //mcInt.errorAnalysisBinInt(gmm,py,nLabels);
        
        for(int y=0;y<nLabels;y++){
            //arguments gmm, distribution of the proposal, metropolis, is plot
            //risk+=py[y]*mcInt.integrate(gmm,y,CNConstants.UNIFORM, true,false);
            //last paramenter, number of trials, when -1 default takes place = 50000 iterations
            double integral=0.0;

            
            if(isMC){
                if(nLabels>2)
                    integral=mcInt.integrate(gmm,y,CNConstants.UNIFORM, false,false,numIters);
                else
                    integral=mcInt.integrateBinaryCase(gmm,y,CNConstants.UNIFORM, false,false,numIters);
            }else
                integral=mcInt.trapeziumMethod(gmm,y,numIters);

            //System.out.println("Numerical Integration Integral: "+integral);
            risk+=py[y]*integral;
                
        }
        
        return risk;
    }

       public float computeROfThetaNumInt( Margin margin, boolean isMC, int numIters) {
        
        //final float[] priors = computePriors(sclassifier,model);
        final float[] priors = AnalyzeLClassifier.getPriors();
        if(priors.length>2)
            isMC=true;          
        // get scores
        GMMDiag gmm = new GMMDiag(priors.length, priors);
        gmm.train(margin);
        /*System.out.println("mean=[ "+gmm.getMean(0, 0)+" , "+gmm.getMean(0, 1)+";\n"+
        +gmm.getMean(1, 0)+" , "+gmm.getMean(1, 1)+"]");
        System.out.println("sigma=[ "+gmm.getVar(0, 0, 0)+" , "+gmm.getVar(0, 1, 1)+";\n"+
        +gmm.getVar(1, 0, 0)+" , "+gmm.getVar(1, 1, 1));
        */
        System.out.println("GMM trained");
        
        
        //return computeR(gmm, priors,marginMAP.get(sclassifier).getNlabs() );
        
        float r= computeRNumInt(gmm, priors,margin.getNlabs(), isMC, numIters ); //xtof
        
        
        
        return r;
        
    }      

    @Override
    public Pair<Integer, Double> process(Pair<Integer, Margin> classInfoPerThread) {
        ColumnDataClassifier columnDataClass = new ColumnDataClassifier(AnalyzeLClassifier.PROPERTIES_FILE);
        
        final float eps = 0.1f;  
        Integer thrId = classInfoPerThread.first();
        //PlotAPI plotR = new PlotAPI("R vs Iterations_Grad Thread_"+thrId,"Iterations", "R");
        //PlotAPI plotF1 = new PlotAPI("F1 vs Iterations_Grad Thread_"+thrId,"Iterations", "F1");        
        Margin margin = classInfoPerThread.second();
        String currentClassifier=AnalyzeLClassifier.CURRENTSETCLASSIFIER;
        int nLabels = margin.getNlabs();
        
        LinearClassifier model = margin.getClassifier();
        
        List<List<Integer>> featsperInst = margin.getFeaturesPerInstances();
        
        Double lastRisk=0.0;
        int counter=0;
        double[] scores= new double[featsperInst.size()];
        Arrays.fill(scores, 0.0);
        float estimr0=AnalyzeLClassifier.CURRENTPARENTESTIMR0;
        //plotR.addPoint(counter, estimr0);
        columnDataClass.testClassifier(model, AnalyzeLClassifier.TESTFILE.replace("%S", currentClassifier));
        double f1=ColumnDataClassifier.macrof1;
        if(!currentClassifier.equals(CNConstants.ALL))
                f1=columnDataClass.fs.get(currentClassifier);
        //the following evaluation is used just to verify that the gradient do not degrade the f-measure on the train set
        
        double f1trainOr=AnalyzeLClassifier.CURENTPARENTF10;
        //plotF1.addPoint(counter,f1);   
        //copy the orginal weights before applying coordinate gradient
        margin.copyOrWeightsBeforGradient();
        HashSet<String> emptyfeats = new HashSet<>();
        for (int iter=0;iter<niters;iter++) {
            int dimIdx=0;
            if(nLabels>2){
                for(int d=1; d<nLabels; d++)
                    margin.setSubListOfFeats(d);
                dimIdx = rnd.nextInt(nLabels);
            }
            List<Double> weightsForFeat= margin.getSubListOfFeats(dimIdx);
            //int selectedFeats[] = margin.getTopWeights(0.3,50);
            List<Integer> trainFeats = margin.getTrainFeatureIndexes();
            List<Integer> testFeats = margin.getTestFeatureIndexes();
            List<Integer> trainFeatsInSubSet = new ArrayList<>();
            for(int index=0; index<trainFeats.size();index++){
                int shIdx=margin.getShuffledIndexFromOriginal(testFeats.get(index));
                if(margin.isIndexInSubset(shIdx))
                    trainFeatsInSubSet.add(shIdx);
                    
                    
            }            
            List<Integer> testFeatsInSubSet = new ArrayList<>();
            for(int index=0; index<testFeats.size();index++){
                int shIdx=margin.getShuffledIndexFromOriginal(testFeats.get(index));
                if(margin.isIndexInSubset(shIdx))
                    testFeatsInSubSet.add(shIdx);
                    
                    
            }
            
            final double[] gradw = new double[weightsForFeat.size()];
            double rndVal = rnd.nextDouble();   
            int featIdx=rnd.nextInt(weightsForFeat.size());
            if(rndVal<0.9 && !testFeatsInSubSet.isEmpty()){
                int selfeatIdx=rnd.nextInt(testFeatsInSubSet.size());
                featIdx=testFeatsInSubSet.get(selfeatIdx)-margin.getSubSetStartIndex();
      
            }else if(rndVal<0.1 && !trainFeatsInSubSet.isEmpty()){
                int selfeatIdx=rnd.nextInt(trainFeatsInSubSet.size());
                featIdx=trainFeatsInSubSet.get(selfeatIdx)-margin.getSubSetStartIndex();
      
            }
            
            int orIdx=margin.getOrIndexFromShuffled(featIdx);
            System.out.println("************ Changing feature :"+orIdx);
            //takes one feature randomly

            double w0 =weightsForFeat.get(featIdx);
            System.out.println("****** w0="+w0);
            
            if (emptyfeats.contains("["+featIdx+","+0+"]")) 
                continue;
            
            float delta = 0.5f;

            double deltaW=w0 + w0*delta;
            System.out.println("****** deltaW="+deltaW);
            weightsForFeat.set(featIdx, deltaW);
            margin.updatingStocGradientStep(0,featIdx, weightsForFeat.get(featIdx));
            float estimr = (isCloseForm)?computeROfTheta(margin):computeROfThetaNumInt(margin,isMonteCarloNI,numIterNumIntegr);

            gradw[0] = (estimr-estimr0)/(w0*delta);
            System.out.println("grad "+gradw[0]);
            System.out.println("****** w0="+w0);
            weightsForFeat.set(featIdx, w0); 
            margin.updatingStocGradientStep(0,featIdx, weightsForFeat.get(featIdx));
            if (gradw[0]==0) 
                    emptyfeats.add("["+featIdx+","+0+"]");
            else{  
                weightsForFeat.set(featIdx,weightsForFeat.get(featIdx)- gradw[0] * eps);                    
                margin.updatingStocGradientStep(0,featIdx, weightsForFeat.get(featIdx));
                System.out.println("Iteration["+iter+"] Updated feature "+ margin.getOrIndexFromShuffled(featIdx));
                columnDataClass.testClassifier(model, AnalyzeLClassifier.TRAINFILE.replace("%S", currentClassifier));
                double f1train=ColumnDataClassifier.macrof1;
                    if(!currentClassifier.equals(CNConstants.ALL))
                        f1train=columnDataClass.fs.get(currentClassifier);
                if(f1train<f1trainOr){
                    System.out.println("Iteration["+iter+"] Not accepted previous step of gradient ");   
                    weightsForFeat.set(featIdx,w0); 
                    margin.updatingStocGradientStep(0,featIdx, weightsForFeat.get(featIdx));
                }    
            }  
            counter++;
            estimr0 =(isCloseForm)?computeROfTheta(margin):computeROfThetaNumInt(margin,isMonteCarloNI,numIterNumIntegr);
            System.out.println("*******************************"); 
            System.out.println("R["+iter+"] = "+estimr0);   
            lastRisk=(double)estimr0;
            //plotR.addPoint(counter, estimr0);
            System.out.println("*******************************");
            
            
            model.setWeights(margin.getWeights());
            
            columnDataClass.testClassifier(model, AnalyzeLClassifier.TESTFILE.replace("%S", currentClassifier));
            f1=ColumnDataClassifier.macrof1;
            if(!currentClassifier.equals(CNConstants.ALL))            
                f1=columnDataClass.fs.get(currentClassifier);
            //plotF1.addPoint(counter, f1);
            System.out.println("*******************************"); 

            //Histoplot.showit(margin.getScoreForAllInstancesLabel0( featsperInst, scores), featsperInst.size());
            //save the model regularly
            if(iter%30==0){
                File mfile = new File(margin.getBinaryFile());
//                for(int i=0; i<weightsForFeat.size();i++){
//                    AnalyzeLClassifier.CURRENTPARENTMARGIN.setWeight(orIdx, margin.getPartialShuffledWeight(i));
//                    
//                }                
                try {
                    IOUtils.writeObjectToFile(model, mfile);
                } catch (IOException ex) {

                }
           }                
        }
        for(String emptyW:emptyfeats){
            System.out.println(emptyW);
        }
        return new Pair(thrId,lastRisk);
    }

    @Override
    public ThreadsafeProcessor<Pair<Integer, Margin>, Pair<Integer, Double>> newInstance() {
        return this;
    }







   
}
  
} 