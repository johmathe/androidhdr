package edu.stanford.ee368.hdrserver;

import jjil.core.RgbImage;
import jjil.core.RgbVal;

/**
 * Merge and tonemaps image
 * @author Johan Mathe
 *
 */
public class HdrAlgorithm {
	private RgbImage inIm[];
	private RgbImage outIm;
	private SfpImage hdrIm;
	private double exposures[];
	private static final int tMax = 255;
	private static final int tMin = 0;
	// Epsilon used for stability of the logarithm.
	private static final double d = 0.00001f;
	// Correction coefficient.
	private static final double a = 0.60f;
	
	static final double RGB2XYZ[][] = {{0.5141364, 0.3238786,  0.16036376},
										 {0.265068,  0.67023428, 0.06409157},
										 {0.0241188, 0.1228178,  0.84442666}};
	static final double XYZ2RGB[][] = {{ 2.5651,   -1.1665,   -0.3986},
									   {-1.0217,    1.9777,    0.0439},
									   { 0.0753,   -0.2543,    1.1892}};
	/**
	 * HDR algorithm per se, takes RgbImages and convert it to a tone mapped image.
	 * @param images
	 * @param exp
	 */
	public HdrAlgorithm(RgbImage[] images, double exp[]) {
		this.inIm = images;
		if (images == null) {
			throw new Error();
		}
		this.hdrIm = new SfpImage(images[0].getWidth(), images[0].getHeight(), 0);
		this.exposures = exp;
	}
	/**
	 * Checks if input data is correct
	 * @return
	 */
	public boolean ready() {
		if (inIm != null) {
			if (inIm.length > 1) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Merge the images
	 * @return
	 */
	public SfpImage Merge() {
		if (!ready()) {
			throw new Error("Not enough data to merge the pictures.");
		}		
		float[] hdrInternal = hdrIm.getData();
		int rgbData[][] = new int[inIm.length][];
		int totalLength = hdrIm.getHeight()*hdrIm.getWidth(); 
		for (int i=0; i<inIm.length; i++) {
			rgbData[i] = inIm[i].getData();
		}
		int nColor = 3;
		boolean properlyExposed[][] = new boolean[nColor][totalLength];
		boolean overExposed[][] = new boolean[nColor][totalLength];
		boolean underExposed[][] = new boolean[nColor][totalLength];
		int properlyExposedCount[][] = new int[nColor][totalLength];
		int c[] = new int[nColor];
		for (int p=0; p<totalLength; p++) {
			// over each input image
			for (int i=0; i<inIm.length; i++) {
				// Normalize the values bt [0 255]
				// Retrieve 3 color channels
				
				c[0] = RgbVal.getR(rgbData[i][p])-Byte.MIN_VALUE;
				c[1] = RgbVal.getG(rgbData[i][p])-Byte.MIN_VALUE;
				c[2] = RgbVal.getB(rgbData[i][p])-Byte.MIN_VALUE;
				
				for (int j=0; j<c.length; j++) {
					
					if(IsOverExposed(c[j])) {
						overExposed[j][p] |= true;
				
					} else if(IsUnderExposed(c[j])) {
						underExposed[j][p] |= true;
						
					} else {
						properlyExposed[j][p] |= true;
						properlyExposedCount[j][p] += 1;
						hdrInternal[3*p+j] += c[j]/exposures[i];
						
					}
				}
			}
		}
		float minProperlyExposed = Float.POSITIVE_INFINITY;
		for (int j=0;j<nColor;j++) {
			for (int p=0; p<totalLength; p++) {
				if(properlyExposed[j][p] && hdrInternal[3*p+j]<minProperlyExposed) {
					minProperlyExposed = hdrInternal[3*p+j];
				}
			}
		}
		float maxProperlyExposed = Float.NEGATIVE_INFINITY;
		for (int j=0;j<nColor;j++) {
			for (int p=0; p<totalLength; p++) {
				if(properlyExposed[j][p] && hdrInternal[3*p+j]>maxProperlyExposed) {
					maxProperlyExposed = hdrInternal[3*p+j];
				}
			}
		}
		System.out.println("maxProperlyExposed"+maxProperlyExposed+"minProperlyExposed"+minProperlyExposed);
		for (int j=0;j<nColor;j++) {
			for (int p=0; p<totalLength; p++) {

				hdrInternal[3*p+j] /= (double)(Math.max(1, properlyExposedCount[j][p]));
				if (!properlyExposed[j][p]) {
					if (underExposed[j][p] && !overExposed[j][p]) {
						hdrInternal[3*p] = minProperlyExposed;
						hdrInternal[3*p+1] = minProperlyExposed;
						hdrInternal[3*p+2] = minProperlyExposed;
					} else if (overExposed[j][p] && !underExposed[j][p]) {
						hdrInternal[3*p] = maxProperlyExposed;
						hdrInternal[3*p+1] = maxProperlyExposed;
						hdrInternal[3*p+2] = maxProperlyExposed;
					} else if (overExposed[j][p] && underExposed[j][p]) {
						//TODO(assign neighbor value)
						System.out.println("over and under");
						hdrInternal[3*p] = 120;
						hdrInternal[3*p+1] = 120;
						hdrInternal[3*p+2] = 120;
					} else {
						throw new Error("not properly exposed and neither under or overexposed pixel");
					}
				}
			}
		}
		
		return hdrIm;
	}
	/**
	 * Checks if a pixel is over exposed
	 * @param v
	 * @return
	 */
	static private boolean IsOverExposed(int v) {
		return v > tMax;
	}
	/**
	 * Check if a pixel is underexposed
	 * @param v
	 * @return
	 */
	static private boolean IsUnderExposed(int v) {
		return v < tMin;
	}
	/**
	 * Tonemaps the image
	 * @return
	 */
	public RgbImage Tonemap() {
		float[] hdrInternal = hdrIm.getData();
		if (hdrInternal == null) {
			throw new Error("HDR image not initialized");
		}
		hdrIm = RGBtoYxy(hdrIm);
		hdrIm = ComputeNewLuminanceMap(hdrIm);
		outIm = YxytoRGB(hdrIm);
		return outIm;
	}
	/**
	 * Gets the max of a float array
	 * @param in
	 * @return
	 */
	static private float Max(float in[]) {
		float max = Float.NEGATIVE_INFINITY;
		if (in == null) {
			throw new Error();
		}
		for (int i=0; i<in.length; i++) {
			if (in[i] > max) {
				max = in[i];	
			}
		}
		return max;
	}
	/**
	 * Gets the minimum of a float array
	 * @param in
	 * @return
	 */
	static private float Min(float in[]) {
		float min = Float.POSITIVE_INFINITY;
		if (in == null) {
			throw new Error();
		}
		for (int i=0; i<in.length; i++) {
			if (in[i] < min) {
				min = in[i];	
			}
		}
		return min;
	}

	/**
	 * Converts an RGB image to a Yxy image
	 * @param in
	 * @return
	 */
	public static SfpImage RGBtoYxy(SfpImage in) {
		  double result[] = new double[3];
		  double W;
		  int totalLength = in.getHeight()*in.getWidth();
		  SfpImage out = new SfpImage(in.getWidth(), in.getHeight());
		  float outData[] = out.getData();
		  float inData[] = in.getData(); 
		  for (int p=0; p<totalLength; p++) {
			  result[0] = result[1] = result[2] = 0.;
			  for (int i = 0; i < 3; i++) {
					for (int j = 0; j < 3; j++) {
						result[i] += RGB2XYZ[i][j] * inData[3*p+j];
					}
			  }
			  W = result[0] + result[1] + result[2];
			  if (W > 0) {
				  outData[3*p+0] = (float)(result[1]);         /* Y */
				  outData[3*p+1] = (float)(result[0] / W);     /* x */
				  outData[3*p+2] = (float)(result[1] / W);     /* y */
		      } else {
		    	  outData[3*p+0] = 0;
		    	  outData[3*p+1] = 0;
		    	  outData[3*p+2] = 0;
		      }
		  }			
		  return out;
	}
	/**
	 * Converts an Yxy image to an RGB image
	 * @param in
	 * @return
	 */
	public static RgbImage YxytoRGB(SfpImage in) {
		  double result[] = new double[3];
		  byte c[] = new byte[3];
		  double X, Y, Z;
		  int totalLength = in.getHeight()*in.getWidth();
		  RgbImage out = new RgbImage(in.getWidth(), in.getHeight());
		  int outData[] = out.getData();
		  float tmpData[] = new float[3*totalLength];
		  float inData[] = in.getData(); 
		 
		  for (int p=0; p<totalLength; p++) {
			  Y         = inData[3*p+0];        /* Y */
		      result[1] = inData[3*p+1];        /* x */
		      result[2] = inData[3*p+2];        /* y */
		      
		      if ((Y > 0.) && (result[1] > 0.) && (result[2] > 0.)) {
		    	  X = (result[1] * Y) / result[2];
		    	  Z = (X/result[1]) - X - Y;
		      } else {
		    	  X = 0;
		    	  Z = 0.;
		      }
		      for (int i=0;i<3;i++) {
		    	  tmpData[3*p+i] = (float)(XYZ2RGB[i][0]*X+XYZ2RGB[i][1]*Y+XYZ2RGB[i][2]*Z);
		      }  
		  }	
		  Normalize(tmpData);
		  for (int p=0; p<totalLength; p++) {
			 for (int i=0; i<3; i++) {
				 c[i] = (byte)(tmpData[3*p+i]*255 - 128);
			 }
			 outData[p] = RgbVal.toRgb(c[0], c[1], c[2]);
		  }
		  return out; 
	}
	/**
	 * Compute the Log Luminance of an hdr image
	 * @param in
	 * @return
	 */
	public static double ComputeLogLuminance(SfpImage in) {
		// TODO(johmathe): assert image is type YSB
		// We don't use the java coding standards to get as close as possible to the paper reinhard2002
		double sumLog = 0;
		float inData[] = in.getData();
		int imSize = in.getHeight()*in.getWidth();
		System.out.println("d:" + d);
		for (int i=0; i<imSize; i++) {
			// Luminance is third component in YSB
			// TODO(johmathe): implement functions like getLuminance?
			sumLog += Math.log(inData[3*i]+d);
		}
		double Lw = Math.exp(sumLog/imSize);
		return Lw;
	}
	/**
	 * Compute the luminance map of an HDR image
	 * @param in
	 * @return
	 */
	public static SfpImage ComputeNewLuminanceMap(SfpImage in) {
		double Lw = ComputeLogLuminance(in);
		System.out.println("Log luminance: "+Lw);
		SfpImage out = new SfpImage(in.getWidth(), in.getHeight());
		float inData[] = in.getData();
		float outData[] = out.getData();
		int imSize = in.getHeight()*in.getWidth();
		double tmpL;
		double l=0;
		for (int p=0; p<imSize; p++) {
			tmpL = (a/Lw)*inData[3*p];
			// Normalization between 0 and 1
			l = tmpL/(1+tmpL);
			outData[3*p] = (float)(l);
			outData[3*p+1] = inData[3*p+1];
			outData[3*p+2] = inData[3*p+2];
		}
		System.out.println("max XYY: "+ Max(outData)+ " min XYY: " + Min(outData));
		return out;
	}
	public static void Normalize(float v[]) {
		float min[] = new float[3];
		float max[] = new float[3];
		float delta[] = new float[3];
		// init min/max
		for (int i=0; i<min.length; i++) {
			min[i] = Float.POSITIVE_INFINITY;
		}
		for (int i=0; i<max.length; i++) {
			max[i] = Float.NEGATIVE_INFINITY;
		}
		// Retrieve info on a per channel basis
		for (int p=0; p<v.length/3; p++) {
			for (int i=0; i<max.length; i++) {
				if(v[3*p+i] > max[i]) {
					max[i] = v[3*p+i]; 
				}
			}
			for (int i=0; i<min.length; i++) {
				if(v[3*p+i] < min[i]) {
					min[i] = v[3*p+i]; 
				}
			}
		}
		
		System.out.println("max0 = " + max[0] + " max1 = " + max[1] + " max2 = " + max[2]);
		for (int i=0; i<delta.length;i++) {
			delta[i] = max[i]-min[i];
		}
		for(int p=0; p<v.length/3; p++) {
			for (int i=0; i<delta.length;i++) {
				if (v[p*3+i]>1) {
					v[p*3+i] = 1;
				}
				if (v[p*3+i]<0) {
					v[p*3+i] = 0;
				}
			}
		}
	}
}
