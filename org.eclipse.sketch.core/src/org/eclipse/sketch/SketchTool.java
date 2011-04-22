/*******************************************************************************
 * Copyright (c) 2010 Ugo Sangiorgi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Ugo Sangiorgi <ugo.sangiorgi@gmail.com> - Initial contribution
 *******************************************************************************/
package org.eclipse.sketch;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.tools.AbstractTool;
import org.eclipse.gmf.runtime.diagram.ui.parts.DiagramEditor;
import org.eclipse.gmf.runtime.emf.type.core.IElementType;
import org.eclipse.sketch.clientobserver.ISketchListener;
import org.eclipse.sketch.ui.views.SketchRecognizerControlView;
import org.eclipse.sketch.util.PointList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;


/**
 * Basic SketchTool, provides some abstract methods to be overwritten by client tools
 * @author  Ugo Sangiorgi
 */
public abstract class SketchTool extends AbstractTool{
	
	//the points of the current sketch
	protected ArrayList<Point> points = new ArrayList<Point>();

	protected ArrayList<Point> quantizedPoints = new ArrayList<Point>();
	
	protected SketchManager manager = new SketchManager();

	private MonitorThread thread = new MonitorThread(Display.getCurrent());
	
	private Color color;
	private Color sampleColor;
	private Color interpColor;
	
	
	//Default Parameters
	int grid = 6;
	long penupdownTolerance = 1500;
	
	boolean showSamples = false;
	
	protected GC gc;

	protected int count = 0;		
	protected long penuptime = -1;
	
	
	//abstract methods 
	public abstract ArrayList getTypes();
	public abstract IElementType getConnection();
	public abstract IElementType getDashedConnection();
	
	public abstract ISketchListener getClient();
	
	public abstract RGB getStrokeColor();
	public abstract Cursor getCursor();
	
	
	public SketchTool() {
		super();
		
		System.out.println("SketchTool is activated");
		
		setDefaultCursor(getCursor());		
		
		manager.setEditor((DiagramEditor) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor());
		manager.attach(getClient());
		

		SketchRecognizerControlView control = ((SketchRecognizerControlView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView("org.eclipse.sketch.ui.views.SketchRecognizerControlView"));
		penupdownTolerance  = control!=null?control.getTolerance():1600;		
		grid = control!=null?control.getGridSize():2;
		showSamples = control!=null?control.getShowSamples():false;
		
		
		gc = new GC(manager.getEditor().getDiagramGraphicalViewer().getControl());
		
		gc.setAntialias(SWT.ON);
		
		int linewidth = control!=null?control.getLineWidth():1;	
		gc.setLineWidth(linewidth);
		color = new Color(gc.getDevice(),getStrokeColor());
		gc.setForeground(color);

		sampleColor = new Color(gc.getDevice(),160,0,60);
		interpColor = new Color(gc.getDevice(),0,160,60);
		
		SketchBank.getInstance().setTypes(getTypes());
		
		
		manager.setTypeForConnection(getConnection());
		manager.setTypeForDashedConnection(getDashedConnection());
		
		
		thread.start();
	}

	
	
	
	@Override
	public void deactivate() {
		thread.done = true;

		try{
			
			SketchRecognizerControlView view = (SketchRecognizerControlView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView("org.eclipse.sketch.ui.views.SketchRecognizerControlView");			
			if(view != null){
			manager.detach(view.getControl());
			}
			}catch(Exception e){
				//e.printStackTrace();
			}
		super.deactivate();
	}


	@Override
	public void activate() {
		try{
			SketchRecognizerControlView view = (SketchRecognizerControlView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView("org.eclipse.sketch.ui.views.SketchRecognizerControlView");
			if(view != null){
				view.getControl().setTypes(SketchBank.getInstance().getAvailableTypes());
				manager.attach(view.getControl());
			}
			}catch(Exception e){
				//e.printStackTrace();
			}
		super.activate();
	}

	
	
	@Override
	public boolean handleButtonDown(int button) {
		penuptime=-1;

		Point qp = new Point();
		qp.setLocation(Math.round(getLocation().x / grid) * grid, Math.round(getLocation().y / grid) * grid);
		quantizedPoints.add(qp);
		
		points.add(getLocation());
		
		prev_qp = null;		
		
		return super.handleButtonDown(button);
	}
	
	
	private Point prev_qp=null;
	
	@Override
	protected boolean handleDrag() {
		
		Point p = new Point();
		p.setLocation(getLocation().x,getLocation().y);
		points.add(p);
		

		if(points.size()>1 && points.get(points.size()-2).x == -1){
			points.add(getLocation());
		}
		
		
		Point point1 = points.get(points.size()-2);
		Point point2 = points.get(points.size()-1);
		
		 /*euclidean distance ..?
		double pqx = (point2.x - point1.x); pqx*=pqx;
		double pqy = (point2.y - point1.y); pqy*=pqy;
		System.out.println(Math.sqrt(pqx+pqy));
		if(Math.sqrt(pqx+pqy)>=2){
			gc.setAlpha(70);
		}else{
			gc.setAlpha(100);
		}
		*/
		
		//updates the editor view
		gc.drawLine(point1.x, point1.y, point2.x, point2.y);

		count++;
		if(count==grid)
		{		
			Point location = getLocation();
			Point qp = new Point();
			qp.setLocation(Math.round(location.x / grid), Math.round(location.y / grid));
			
			if (prev_qp!=null)
			{
				Dimension diff = qp.getDifference(prev_qp);
				
				if (Math.abs(diff.width) > 1 || Math.abs(diff.height) > 1)
				{					
					int max_diff = (int)Math.max(Math.abs(diff.width), Math.abs(diff.height));
					float deltax = diff.width  / (float)max_diff * 1;
					float deltay = diff.height / (float)max_diff * 1;
					
					for (int i=0; i<max_diff; i++)
					{
						Point interp = new Point(prev_qp);
						interp.x += deltax*i;
						interp.y += deltay*i;
						addQP(interp, true);
					}
						
					/*}
					float a = (prev_qp.y-qp.y)/(float)(prev_qp.x-qp.x);
					float b = qp.y - a*qp.x;*/
					
				}
			}
					
			//Final point, the one that is recorded
			addQP(qp, false);
			
			count=0;
			prev_qp = qp;
		}
		
			
		return true;
	}
	
	private void addQP(Point qp, boolean interpolated)
	{
		quantizedPoints.add(qp.getScaled(grid));

		if(showSamples)
		{
			if (interpolated)
				gc.setForeground(interpColor);
			else
				gc.setForeground(sampleColor);
			
			gc.drawRectangle(qp.x*grid,qp.y*grid,2,2);
			gc.setForeground(color);
			
		}	
	}
	
/*

	private void drawGrid(GC gc){
		gc.setLineWidth(1);
		
		for(int i=0;i<editor.getDiagramGraphicalViewer().getControl().getSize().x;i=i+grid){			
			gc.drawLine(i, 0, i,editor.getDiagramGraphicalViewer().getControl().getSize().y );
		}
		for(int i=0;i<editor.getDiagramGraphicalViewer().getControl().getSize().y;i=i+grid){			
			gc.drawLine(0, i, editor.getDiagramGraphicalViewer().getControl().getSize().x,i);
		}
		gc.setLineWidth(2);
	}
	
	*/
	
	
	@Override
	public boolean handleButtonUp(int button) {
		penuptime = System.currentTimeMillis();

		points.add(new Point(-1,-1));
		quantizedPoints.add(new Point(-1,-1));
		
		prev_qp = null;
		
		
		return super.handleButtonUp(button);
	}
	
	
	
	
	public void cleanup(){
		points.clear();
		quantizedPoints.clear();
		
		prev_qp = null;
		
		penuptime = -1;
	}


	@Override
	protected String getCommandName() {
		return null;
	}

	/**
	 * 
	 * Creates a new unprocessed Sketch and passes it to the Manager
	 * 
	 * @author ugo
	 */
	class MonitorThread extends Thread {  
		private Display d;  

		public boolean done = false;
		public MonitorThread(Display _d){  
			d = _d;  
		}  

		public void run(){  

			while (!done){  
				d.asyncExec(new Runnable(){  
					public void run(){  


						if(penuptime>0){

							if(System.currentTimeMillis()-penuptime>penupdownTolerance){

								penuptime=-1;
								Sketch sketch = new Sketch();
								
								PointList list = new PointList();
								list.addAll(points);

								sketch.eSet(SketchPackage.SKETCH__POINTLIST, list);
								
								sketch.eSet(SketchPackage.SKETCH__POINTS, points.clone());
								sketch.eSet(SketchPackage.SKETCH__QUANTIZED_POINTS, quantizedPoints.clone());
								
								manager.newSketch(sketch);

								//erases the drawing area
								manager.getEditor().getDiagramGraphicalViewer().getControl().redraw();	 

								
								cleanup();
							}
						}
					}  

				});  
				
				try {  
					Thread.sleep(penupdownTolerance/3);  
				} catch (InterruptedException e) {  
					e.printStackTrace();  
				}  
			}  
		}  
	}  
}