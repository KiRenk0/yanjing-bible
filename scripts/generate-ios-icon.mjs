// Rasterize the existing Android vector logo for Apple's required opaque 1024px icon.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
const root=path.resolve(import.meta.dirname,'..');
const dir=path.join(root,'ios/YanJing/Resources/Assets.xcassets/AppIcon.appiconset');
fs.mkdirSync(dir,{recursive:true});
const curve=(a,b,c)=>Array.from({length:41},(_,i)=>{const t=i/40;return [(1-t)**2*a[0]+2*(1-t)*t*b[0]+t*t*c[0],(1-t)**2*a[1]+2*(1-t)*t*b[1]+t*t*c[1]];});
const left=[...curve([24,28],[40,23],[52,32]),[52,80],...curve([52,80],[39,70],[24,75])];
const right=[...curve([56,32],[70,23],[84,28]),[84,75],...curve([84,75],[68,70],[56,80])];
const strokes=[[[31,38],[40,36],[46,40]],[[31,47],[40,45],[46,49]],[[64,40],[72,36],[78,38]],[[64,49],[72,45],[78,47]]].map(p=>curve(...p));
function inside(x,y,polygon){let result=false;for(let i=0,j=polygon.length-1;i<polygon.length;j=i++){const a=polygon[i],b=polygon[j];if((a[1]>y)!==(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])result=!result;}return result;}
function stroke(x,y){if(x<29||x>80||y<34||y>51)return false;return strokes.some(points=>points.some((b,i)=>{if(!i)return false;const a=points[i-1],dx=b[0]-a[0],dy=b[1]-a[1];const t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));return (x-a[0]-t*dx)**2+(y-a[1]-t*dy)**2<=1;}));}
const size=1024,raw=Buffer.alloc(size*(size*3+1));
for(let y=0;y<size;y++)for(let x=0;x<size;x++){const px=(x+.5)*108/size,py=(y+.5)*108/size;const cream=(inside(px,py,left)||inside(px,py,right))&&!stroke(px,py);const rgb=cream?[247,241,223]:[36,91,73];const off=y*(size*3+1)+1+x*3;raw.set(rgb,off);}
function crc(bytes){let crc=0xffffffff;for(const b of bytes){crc^=b;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
function chunk(name,bytes){const type=Buffer.from(name);const size=Buffer.alloc(4);size.writeUInt32BE(bytes.length);const checksum=Buffer.alloc(4);checksum.writeUInt32BE(crc(Buffer.concat([type,bytes])));return Buffer.concat([size,type,bytes,checksum]);}
const header=Buffer.alloc(13);header.writeUInt32BE(size,0);header.writeUInt32BE(size,4);header[8]=8;header[9]=2;
fs.writeFileSync(path.join(dir,'AppIcon.png'),Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
fs.writeFileSync(path.join(dir,'Contents.json'),JSON.stringify({images:[{filename:'AppIcon.png',idiom:'universal',platform:'ios',size:'1024x1024'}],info:{author:'xcode',version:1}},null,2)+'\n');
fs.writeFileSync(path.join(dir,'../Contents.json'),JSON.stringify({info:{author:'xcode',version:1}},null,2)+'\n');
