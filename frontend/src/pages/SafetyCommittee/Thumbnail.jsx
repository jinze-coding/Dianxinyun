import React, { useEffect, useState } from 'react';
import { committee, committeeContent } from '../../services/safetyCommittee';
export default function Thumbnail({ file, localFile, large = false, onClick, reportError }) {
  const [url,setUrl]=useState('');const [local,setLocal]=useState('');const [failed,setFailed]=useState(false);const [retry,setRetry]=useState(0);
  const extension=(file?.extension||localFile?.name?.split('.').pop()||'').toUpperCase();
  const video=['MP4','MOV','M4V','WEBM','MKV','AVI'].includes(extension);
  const photo=['JPG','JPEG','PNG','GIF','BMP','WEBP','HEIC','HEIF'].includes(extension);
  useEffect(()=>{
    if(!localFile||!photo){setLocal('');return undefined;}
    const next=URL.createObjectURL(localFile);setLocal(next);return()=>URL.revokeObjectURL(next);
  },[localFile,photo]);
  useEffect(()=>{
    let alive=true,generation=0,timer;let controller;
    setUrl('');setFailed(false);
    const load=async(retryRequest=false)=>{
      clearTimeout(timer);const current=++generation;
      if(document.hidden||!file?.id)return;
      controller?.abort();controller=new AbortController();
      try{
        const state=await committee.thumbnail(file.id,retryRequest,controller.signal);
        if(!alive||current!==generation)return;
        if(state.status==='READY'){
          await committee.read(file.id);
          if(alive&&current===generation){setFailed(false);setUrl(`${committeeContent(file.id,true,true)}&v=${file.rotationVersion||1}`);}
        }else if(state.status==='FAILED')setFailed(true);
        else timer=setTimeout(()=>void load(),2000);
      }catch(e){if(alive&&current===generation&&e.name!=='CanceledError'&&e.name!=='AbortError'){setUrl('');setFailed(true);reportError?.(e);}}
    };
    const visibility=()=>{generation++;controller?.abort();clearTimeout(timer);if(document.hidden)setUrl('');else void load();};
    void load(retry>0);document.addEventListener('visibilitychange',visibility);
    return()=>{alive=false;generation++;clearTimeout(timer);controller?.abort();document.removeEventListener('visibilitychange',visibility);};
  },[file?.id,file?.rotationVersion,retry]);
  const source=url||((file?.rotationDegrees||0)===0?local:'');
  return <button type="button" className={`sc-thumb${large?' sc-thumb-large':''}`} onClick={()=>failed&&!source?setRetry(retry+1):onClick?.()} title={file?.fileName||localFile?.name||'附件'} aria-label={failed&&!source?'重试缩略图':'预览附件'}>
    {source?<img src={source} alt="附件缩略图" className={!photo&&!video?'sc-thumb-document':''} onError={()=>{setUrl('');setLocal('');setFailed(true);}}/>:<span className="sc-thumb-placeholder"><strong>{extension||'附件'}</strong>{large&&<small>{failed?'点击重试':file?'生成缩略图…':'等待上传'}</small>}</span>}
    {source&&video&&<span className="sc-thumb-play">▶</span>}{source&&!photo&&!video&&<span className="sc-thumb-badge">{extension} · 首页</span>}
  </button>;
}
