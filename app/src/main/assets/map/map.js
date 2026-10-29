/* WanderTrace map-only renderer. Leaflet 1.9.4 is bundled locally under BSD-2-Clause. */
(function () {
  'use strict';
  var map = L.map('map', {zoomControl:false, attributionControl:true, zoomSnap:0,
    zoomDelta:0.25, minZoom:2, maxZoom:20, touchZoom:true, bounceAtZoomLimits:false,
    inertia:true, zoomAnimation:true, fadeAnimation:true, preferCanvas:true});
  map.attributionControl.setPrefix(false);
  map.setView([39.9,116.4],14);
  var config=null, base=null, labels=null, oldLayers=[], line=null, dots=L.layerGroup().addTo(map);
  var revision=-1, sequence=-1, pointsRevision=-1, statusTimer=0, cameraFrame=0;
  var statusNode=document.getElementById('status');
  function bridge(method,args) {
    if(window.WanderBridge && typeof window.WanderBridge[method]==='function') {
      try {window.WanderBridge[method].apply(window.WanderBridge,args);} catch(ignored) {}
    }
  }
  function key(c) {return c.z+'/'+c.x+'/'+c.y;}
  function sourceHost(provider,x,y) {
    return provider==='amap'||provider==='baidu' ? 1+((x+y)&3) : provider==='tencent' ? (x+y)&3 : (x+y)&7;
  }
  var SourceLayer=L.TileLayer.extend({
    getTileUrl:function(coords) {
      var inv=Math.pow(2,coords.z)-1-coords.y;
      return L.Util.template(this._url,{z:coords.z,x:coords.x,y:coords.y,iy:inv,
        gx:coords.x>>4,gy:inv>>4,s:sourceHost(this.options.provider,coords.x,coords.y)});
    }
  });
  function createLayer(pattern,overlay) {
    var layer=new SourceLayer(pattern,{provider:config.provider,minZoom:2,maxZoom:config.maxZoom,
      maxNativeZoom:config.maxNativeZoom,tileSize:256,keepBuffer:2,
      updateWhenIdle:true,updateWhenZooming:false,detectRetina:false,
      attribution:overlay?'':config.attribution,zIndex:overlay?3:2});
    layer.loaded=new Set();layer.failed=new Set();
    layer.on('tileload',function(e){layer.loaded.add(key(e.coords));layer.failed.delete(key(e.coords));scheduleStatus();});
    layer.on('tileerror',function(e){layer.loaded.delete(key(e.coords));layer.failed.add(key(e.coords));scheduleStatus();});
    layer.on('tileunload',function(e){layer.loaded.delete(key(e.coords));layer.failed.delete(key(e.coords));});
    layer.on('load',scheduleStatus);return layer;
  }
  function coverage(layer) {
    if(!layer || !config) return {expected:0,loaded:0,failed:0};
    var z=Math.min(config.maxNativeZoom,Math.max(2,Math.round(map.getZoom()))),n=Math.pow(2,z);
    var bounds=map.getBounds(),nw=map.project(bounds.getNorthWest(),z),se=map.project(bounds.getSouthEast(),z);
    var x0=Math.floor(nw.x/256),x1=Math.floor((se.x-0.000001)/256);
    var y0=Math.max(0,Math.floor(nw.y/256)),y1=Math.min(n-1,Math.floor((se.y-0.000001)/256));
    var result={expected:0,loaded:0,failed:0};
    for(var x=x0;x<=x1;x++) for(var y=y0;y<=y1;y++) {
      var k=z+'/'+((x%n+n)%n)+'/'+y;result.expected++;
      if(layer.loaded.has(k)) result.loaded++;if(layer.failed.has(k)) result.failed++;
    }
    return result;
  }
  function allPointsVisible() {
    if(!line || !config || config.points.length<2) return true;
    return map.getBounds().contains(line.getBounds());
  }
  function snapshot() {
    var b=coverage(base),l=coverage(labels);
    return {revision:revision,zoom:map.getZoom(),dataZoom:config?Math.min(config.maxNativeZoom,Math.round(map.getZoom())):0,
      baseExpected:b.expected,baseLoaded:b.loaded,labelExpected:l.expected,labelLoaded:l.loaded,
      failed:b.failed+l.failed,complete:b.expected>0&&b.loaded===b.expected&&l.loaded===l.expected,
      pointCount:config?config.points.length:0,allPointsVisible:allPointsVisible(),satellite:config?config.satellite:false};
  }
  function reportStatus() {
    if(!config) return;
    var state=snapshot(),title=config.satellite?'卫星图':'道路图';
    statusNode.hidden=state.complete;
    statusNode.textContent=state.failed ? '部分地图未加载，点按此处重新加载' : '正在加载'+title+' '+state.baseLoaded+'/'+state.baseExpected;
    statusNode.style.pointerEvents=state.failed?'auto':'none';
    if(state.complete && oldLayers.length) {oldLayers.forEach(function(layer){map.removeLayer(layer);});oldLayers=[];}
    bridge('status',[revision,state.complete,state.baseLoaded,state.baseExpected,state.labelLoaded,state.labelExpected,state.failed,state.pointCount,state.allPointsVisible]);
  }
  function scheduleStatus() {clearTimeout(statusTimer);statusTimer=setTimeout(reportStatus,40);}
  function reportCamera() {
    cameraFrame=0;if(!config) return;
    var c=map.getCenter();bridge('camera',[revision,sequence,c.lat,c.lng,map.getZoom()]);
  }
  map.on('move',function(){if(!cameraFrame) cameraFrame=requestAnimationFrame(reportCamera);});
  map.on('moveend zoomend',function(){reportCamera();scheduleStatus();});
  function drawRoute() {
    if(line) map.removeLayer(line);dots.clearLayers();line=null;
    if(config.route && config.points.length>1) line=L.polyline(config.points,{color:'#087f6d',weight:4,opacity:1,smoothFactor:0,noClip:false}).addTo(map);
    config.points.forEach(function(p){L.circleMarker(p,{radius:5,color:'#ffffff',weight:2,fillColor:'#087f6d',fillOpacity:1,interactive:false}).addTo(dots);});
  }
  function apply(next) {
    var newSource=next.revision!==revision,newCamera=next.sequence!==sequence,newPoints=next.pointsRevision!==pointsRevision;
    config=next;map.setMaxZoom(config.maxZoom);
    if(newSource) {
      if(base) {base.setZIndex(0);oldLayers.push(base);}
      if(labels) {labels.setZIndex(1);oldLayers.push(labels);}
      while(oldLayers.length>2) map.removeLayer(oldLayers.shift());
      revision=config.revision;base=createLayer(config.base,false);labels=config.labels?createLayer(config.labels,true):null;
      base.addTo(map);if(labels) labels.addTo(map);
    }
    if(newSource||newCamera) {sequence=config.sequence;map.setView(config.center,config.zoom,{animate:false});}
    if(newSource||newPoints) {pointsRevision=config.pointsRevision;drawRoute();}
    map.invalidateSize({pan:false});
    if(config.fit && config.route && line) map.fitBounds(line.getBounds(),{animate:false,padding:[24,24],maxZoom:config.maxZoom});
    reportCamera();scheduleStatus();
  }
  statusNode.addEventListener('click',function(){if(base) {base.loaded.clear();base.failed.clear();base.redraw();}if(labels){labels.loaded.clear();labels.failed.clear();labels.redraw();}scheduleStatus();});
  window.WanderMap={apply:apply,resize:function(){map.invalidateSize({pan:false});scheduleStatus();},snapshot:snapshot,
    setView:function(center,zoom){map.setView(center,zoom,{animate:false});},
    zoomBy:function(delta,point){map.setZoomAround(point||map.getSize().divideBy(2),map.getZoom()+delta,{animate:false});},
    retry:function(){statusNode.click();}};
  bridge('ready',[]);
}());
