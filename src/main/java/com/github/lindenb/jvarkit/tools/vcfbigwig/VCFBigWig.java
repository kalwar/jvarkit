/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation
* 2015 moved to htsjdk + knime

*/
package com.github.lindenb.jvarkit.tools.vcfbigwig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.broad.igv.bbfile.BBFileReader;
import org.broad.igv.bbfile.BigWigIterator;
import org.broad.igv.bbfile.WigItem;

import com.github.lindenb.jvarkit.util.picard.SAMSequenceDictionaryProgress;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import com.github.lindenb.jvarkit.util.vcf.VcfIterator;

public class VCFBigWig extends AbstractVCFBigWig
	{
	private static final org.slf4j.Logger LOG = com.github.lindenb.jvarkit.util.log.Logging.getLog(VCFBigWig.class);

	private BBFileReader bbFileReader=null;
	
	public VCFBigWig()
		{
		}
	
	
	@Override
	public Collection<Throwable> initializeKnime() {
		if(super.biwWigFile==null || super.biwWigFile.isEmpty())
			{
			return wrapException("Undefined BigWig file");
			}
		try
			{
			
			this.bbFileReader= new BBFileReader(super.biwWigFile);
			if(!this.bbFileReader.isBigWigFile())
				{
				this.bbFileReader=null;
				throw new IOException(super.biwWigFile+" is not a bigWIG file.");
				}
			
			if(this.TAG==null || this.TAG.isEmpty())
				{
				super.TAG=super.biwWigFile;
				int i=TAG.lastIndexOf(File.separator);
				if(i!=-1) TAG=TAG.substring(i+1);
				i=super.TAG.indexOf('.');
				super.TAG=super.TAG.substring(0,i);
				LOG.info("setting tag to "+super.TAG);
				}
			
			}
		catch(Exception err)
			{
			return wrapException(err);
			}
		return super.initializeKnime();
		}
	
	@Override
	public void disposeKnime() {
		try
			{
			if(this.bbFileReader!=null)
				{
				CloserUtil.close(this.bbFileReader.getBBFis());
				}
			CloserUtil.close(this.bbFileReader);
			this.bbFileReader=null;
			}
		catch(Exception err)
			{
			LOG.error("Error",err);
			}
		super.disposeKnime();
		}
	
	@Override
	protected Collection<Throwable> call(String inputName) throws Exception {
		return doVcfToVcf(inputName);
		}
	
	@Override
	protected Collection<Throwable> doVcfToVcf(
			final String inputName,
			final VcfIterator r,
			final VariantContextWriter w)
			throws IOException {
		final VCFHeader header=r.getHeader();
		final VCFHeader h2=new VCFHeader(header);
		h2.addMetaDataLine(new VCFInfoHeaderLine(
				super.TAG,1,
				VCFHeaderLineType.Float,
				"Values from bigwig file: "+this.biwWigFile
				));
		super.addMetaData(h2);
		w.writeHeader(h2);
		final SAMSequenceDictionaryProgress progress=new SAMSequenceDictionaryProgress(header);
		
		final List<Float> values=new ArrayList<Float>();
		while(r.hasNext())
			{
			final VariantContext ctx = progress.watch(r.next());
			values.clear();
			
			final BigWigIterator iter=this.bbFileReader.getBigWigIterator(
					ctx.getContig(),
					ctx.getStart()-1,
					ctx.getContig(),
					ctx.getEnd(),
					super.isContained()
					);
			while(iter!=null && iter.hasNext())
				{
				WigItem item=iter.next();
				float v=item.getWigValue();
				values.add(v);
				
				}
			if(values.isEmpty())
				{
				w.add(ctx);
				continue;
				}
			
			double total=0L;
			for(Float f:values) total+=f;
			final VariantContextBuilder b=new VariantContextBuilder(ctx);
			b.attribute(this.TAG,(float)(total/values.size()));
			w.add(b.make());
			}
		progress.finish();
		return RETURN_OK;
		}
	
	
	public static void main(String[] args) throws IOException
		{
		new VCFBigWig().instanceMain(args);
		}
}
