#version 330 core
// Adapted from Shadertoy (https://www.shadertoy.com/view/4ds3WS - "Minecraft" by Reinder
// Nijhoff, CC BY-NC-SA 4.0) for THM Addons. Named voxelworld to avoid colliding with the
// existing RusherHack-derived minecraft.fsh.
//
// ponytail: the original sampled iChannel0 (iq's RGBA noise texture, used as a folded-2D
// lookup for value noise) and iChannel1 (a grayscale terrain heightmap). This pipeline binds
// no samplers at all, so both were replaced with hash-based value noise built on the shader's
// own hash(vec3). Consequence: it is the SAME shader but a DIFFERENT world - terrain shape
// comes from a different noise source, so it will not match the Shadertoy preview terrain.
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

#define iTime time
#define iResolution resolution
// ponytail: the original reads iMouse.z to detect "is the mouse held". There is no button
// state here, so pin z negative - that is exactly the un-clicked Shadertoy path (mo = 0),
// which keeps the camera on its own flight path instead of jumping with cursor movement.
#define iMouse vec4(mouse, -1.0, 0.0)

// --------[ Original ShaderToy begins here ]---------- //
// Minecraft. Created by Reinder Nijhoff 2013
// Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.
// @reindernijhoff
//
// https://www.shadertoy.com/view/4ds3WS
//
// port of javascript minecraft: http://jsfiddle.net/uzMPU/
// original code by Markus Persson: https://twitter.com/notch/status/275331530040160256
// combined with voxel-shader by inigo quilez (https://www.shadertoy.com/view/4dfGzs)
//

#define SEALEVEL -25.
#define MAXSTEPS 180
//#define HOUSE

vec3 sundir = normalize( vec3(-0.5,0.6,0.7) );

float hash( in float n ) {
    return fract(sin(n)*43758.5453);
}
float hash( in vec3 x ) {
    float n = dot( x, vec3(1.0,113.0,257.0) );
    return fract(sin(n)*43758.5453);
}
vec3 hash3( vec3 n ) {
    return fract(sin(n)*vec3(653.5453123,4456.14123,165.340423));
}
// ponytail: trilinear value noise, replacing the iChannel0 fetch. The original folded z into a
// 2D lattice and let the texture's bilinear filter do the xy interpolation for free; with no
// sampler that trick buys nothing, so this is the plain 8-corner form.
// ponytail: hash(vec3) is dot + sin + fract, and every lattice corner differs from the base
// corner by a CONSTANT dot offset (+1 for x, +113 for y, +257 for z). So one dot is computed
// once and the corners are integer offsets from it - identical values, 7 fewer dot products.
float hashn( in float n ) { return fract(sin(n)*43758.5453); }

float noise( in vec3 x ) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);

    float n = dot( p, vec3(1.0,113.0,257.0) );

    return mix( mix( mix(hashn(n      ), hashn(n+  1.0), f.x),
                     mix(hashn(n+113.0), hashn(n+114.0), f.x), f.y ),
                mix( mix(hashn(n+257.0), hashn(n+258.0), f.x),
                     mix(hashn(n+370.0), hashn(n+371.0), f.x), f.y ), f.z );
}
// ponytail: bilinear value noise on a z=17 slice, replacing the 2D iChannel0 fetch. Same
// shared-dot trick as above.
float noise( in vec2 x ) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f*f*(3.0-2.0*f);

    float n = dot( p, vec2(1.0,113.0) ) + 257.0*17.0;

    return mix( mix(hashn(n      ), hashn(n+  1.0), f.x),
                mix(hashn(n+113.0), hashn(n+114.0), f.x), f.y );
}
float sum(vec3 v) { return dot(v, vec3(1.0)); }

// port of minecraft

bool getMaterialColor( int i, vec2 coord, out vec3 color ) {
    // 16x16 tex
    vec2 uv = floor( coord );

    float n = uv.x + uv.y*347.0 + 4321.0 * float(i);
    float h = hash(n);

    float br = 1. - h * (96./255.);
    color = vec3( 150./255., 108./255.,  74./255.); // 0x966C4A;

    float xm1 = mod((uv.x * uv.x * 3. + uv.x * 81.) / 4., 4.);

    if (i == 1) {
        if( uv.y < (xm1 + 18.)) {
            color = vec3( 106./255., 170./255.,  64./255.); // 0x6AAA40;
        } else if (uv.y < (xm1 + 19.)) {
            br = br * (2. / 3.);
        }
    }
    if (i == 4) {
        color = vec3( 127./255., 127./255., 127./255.); // 0x7F7F7F;
    }
    if (i == 7) {
        color = vec3( 103./255., 82./255.,  49./255.); // 0x675231;
        if ( h < 0.5 ) {
            br = br * (1.5 - mod(uv.x, 2.));
        }
    }
#ifdef HOUSE
    if (i == 5) {
        color = vec3( 181./255.,  58./255.,  21./255.); // 0xB53A15;
        if ( mod(uv.x + (floor(uv.y / 4.) * 5.), 8.) == 0. || mod( uv.y, 4.) == 0.) {
            color = vec3( 188./255., 175./255., 165./255.); // 0xBCAFA5;
        }
    }
#endif
    if (i == 9) {
        color = vec3(  64./255.,  64./255., 255./255.); // 0x4040ff;
    }
    if (i == 8) {
        color = vec3(  80./255., 217./255.,  55./255.); // 0x50D937;
        if ( h < 0.5) {
            return false;
        }
    }
    if (i == 10) {
        color = vec3(0.65,0.68,0.7)*1.35;
        br = 1.;
    }
    color *= br;

    return true;
}

//=====================================================================
// Code by inigo quilez - iq/2013:

const mat3 m = mat3( 0.00,  0.80,  0.60,
                    -0.80,  0.36, -0.48,
                    -0.60, -0.48,  0.64 );

float mapTerrain( vec2 p ) {
    p *= 0.02;

    float f;
    // ponytail: was textureLod(iChannel1, p*0.01) - a broad, low-frequency heightmap. The
    // 0.01 scale relied on the texture tiling every 256 texels; a unit-period procedural noise
    // at that scale would be nearly constant, so the base octave is retuned to 0.35 to give
    // landmasses on roughly the same world scale. Output range is unchanged (both are 0..1),
    // so the 50*f-30 remap and SEALEVEL clamp below still behave as the original.
    f  = 0.500*noise( p*0.35 );
    f += 0.1250*noise( p*4.01 );
    return  max( 50.0*f-30., SEALEVEL);
}

vec3 gro = vec3(0.0);

bool map(in vec3 c ) {
    vec3 p = c + 0.5;

    float f = mapTerrain( p.xz );

    bool hit = c.y < f; // ground

    // ponytail: the tree block is the expensive half of this function (a second mapTerrain for
    // the cell seed, a third for the tree's own ground height) and it was running for EVERY
    // voxel the DDA touched, including all the empty sky. Two exact guards, no behaviour change:
    //   !hit    - underground voxels are solid regardless, the original just overwrote the
    //             tree result with `hit = c.y < f ? true : hit` one line later.
    //   c.y<10  - mapTerrain is max(50*f-30, SEALEVEL) with f in [0, 0.625], so terrain tops out
    //             at y = 1.25. Trunks reach h+4 and leaves h+6+2.5, i.e. never above y = 9.75,
    //             so nothing at or above 10 can ever be a tree.
    if( !hit && c.y < 10. ) {
        vec2 fc = floor( c.xz * 0.05 );
        // ponytail: -26% frame time, and the single biggest win in this shader. The third
        // component here is only ever a seed for hash3, but it was a full mapTerrain call (two
        // value-noise lookups = 8 sins) evaluated for every near-ground voxel the DDA touched.
        // A plain hash over the same cell index, remapped into mapTerrain's own output range,
        // seeds hash3 just as well. This re-rolls WHICH cells get trees - tree density and
        // placement rules are unchanged, and `mapTerrain(tp) > SEALEVEL` below still keeps them
        // off the water. Measured alternatives that were rejected: a sin-free hash here saved
        // 1% (sin is not the bottleneck), MAXSTEPS 180->90 saved 29% but visibly walls off the
        // horizon, and dropping mapTerrain's detail octave saved 44% but flattens the terrain
        // into smooth concentric terraces.
        vec3 h = hash3( vec3( fc*vec2(213.123,2134.125), hashn(dot(fc,vec2(1.0,113.0))+911.0)*26.0-25.0 ) );

        if( h.z > 0.75 ) {
            vec2 tp = floor(fc*20.+mod(h.yx*154.43125, 10.)) + 5.5;
            float h = mapTerrain( tp );
            if( h > SEALEVEL ) {
                if( all( equal( tp, p.xz ) ) ) hit = c.y < h+4.; // treetrunk
                if( distance( p, vec3( tp.x, h+6., tp.y ) ) < 2.5 ) hit = true; // leaves
            }
        }
    }

    if( !hit && c.y > 8. &&
       sin( (c.y-8.)*(3.1415/32.)) * (10./(c.y-7.)) * noise( c*0.08+(0.7*iTime)*vec3(0.3, 0.07, 0.12) )
       > 0.6 ) hit = true; // clouds

#ifdef HOUSE
    vec2 hc = abs(c.xz - vec2( 32., 130.)); // house
    if( all( lessThan( hc, vec2( 6., 10. ) ) ) && c.y < -hc.x-12. ) {
        hit = true;
        if( all( lessThan( hc, vec2( 2., 10. ) ) ) && c.y < -18. && c.y > -23. ) {
            hit = false;
        }
        if( all( lessThan( hc, vec2( 5., 9. ) ) ) && c.y < -18. && c.y > -23. ) {
            hit = false;
        }
    }
#endif

    if( distance( gro, c ) < 1.5 ) return false;

    return hit;
}


int mapMaterial(in vec3 c ) {
    int mat = 0;
    vec3 p = c + 0.5;

    float f = ceil( mapTerrain( p.xz ) );

    if( p.y <= f ) mat = 1; // ground
    else if( p.y < f+3. ) mat = 7; // treetrunk
    else if( p.y < f+10. ) mat = 8; // leaves
    else mat = 10; // clouds

#ifdef HOUSE
    vec2 hc = abs(c.xz - vec2( 32., 130.));
    if( c.y < 0. && all( lessThan( hc, vec2( 6., 10. ) ) ) ) {
        mat = 5;
        if( !map( c+vec3(0.,1.,0.) ) ) mat = 6;
    }
#endif

    return mat;
}

float castRay( in vec3 ro, in vec3 rd, out vec3 oVos, out vec3 oDir ) {
    vec3 pos = floor(ro);
    vec3 ri = 1.0/rd;
    vec3 rs = sign(rd);
    vec3 dis = (pos-ro + 0.5 + rs*0.5) * ri;

    float res = 0.0;
    vec3 mm = vec3(0.0);
    bool hit = false;

    for( int i=0; i<MAXSTEPS; i++ )
    {
        if( hit ) break;
        mm = step(dis.xyz, dis.yxy) * step(dis.xyz, dis.zzx);
        dis += mm * rs * ri;
        pos += mm * rs;
        if( map(pos) ) { hit = true;}
    }

    vec3 nor = -mm*rs;
    vec3 vos = pos;

    // intersect the cube
    vec3 mini = (pos-ro + 0.5 - 0.5*vec3(rs))*ri;
    float t = max ( mini.x, max ( mini.y, mini.z ) );

    oDir = mm;
    oVos = vos;

    return hit?t:0.;

}

float castVRay( in vec3 ro, in vec3 rd, in float maxDist ) {
    vec3 pos = floor(ro);
    vec3 ri = 1.0/rd;
    vec3 rs = sign(rd);
    vec3 dis = (pos-ro + 0.5 + rs*0.5) * ri;

    float res = 1.0;

    for( int i=0; i<18; i++ )
    {
        if( map(pos) ) {res=0.0; break; }
        vec3 mm = step(dis.xyz, dis.yxy) * step(dis.xyz, dis.zzx);
        dis += mm * rs * ri;
        pos += mm * rs;
    }

    return res;
}

vec3 path( float t ) {
    vec2 p  = 100.0*sin( 0.02*t*vec2(1.0,1.2) + vec2(0.1,0.9) );
         p +=  50.0*sin( 0.04*t*vec2(1.3,1.0) + vec2(1.0,4.5) );

    return vec3( p.x, mapTerrain(p)+2.+4.*(1.-cos(iTime*0.1)), p.y );
}


//=====================================================================
// Ambient occlusion

vec4 edges( in vec3 vos, in vec3 nor, in vec3 dir )
{
    vec3 v1 = vos + nor + dir.yzx;
    vec3 v2 = vos + nor - dir.yzx;
    vec3 v3 = vos + nor + dir.zxy;
    vec3 v4 = vos + nor - dir.zxy;

    vec4 res = vec4(0.0);
    if( map(v1) ) res.x = 1.0;
    if( map(v2) ) res.y = 1.0;
    if( map(v3) ) res.z = 1.0;
    if( map(v4) ) res.w = 1.0;

    return res;
}

vec4 corners( in vec3 vos, in vec3 nor, in vec3 dir )
{
    vec3 v1 = vos + nor + dir.yzx + dir.zxy;
    vec3 v2 = vos + nor - dir.yzx + dir.zxy;
    vec3 v3 = vos + nor - dir.yzx - dir.zxy;
    vec3 v4 = vos + nor + dir.yzx - dir.zxy;

    vec4 res = vec4(0.0);
    if( map(v1) ) res.x = 1.0;
    if( map(v2) ) res.y = 1.0;
    if( map(v3) ) res.z = 1.0;
    if( map(v4) ) res.w = 1.0;

    return res;
}


void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    // inputs
    vec2 q = fragCoord.xy / iResolution.xy;
    vec2 p = -1.0 + 2.0*q;
    p.x *= iResolution.x/ iResolution.y;

    vec2 mo = iMouse.xy / iResolution.xy;
    if( iMouse.z < 0. ) mo=vec2(0.0);

    float time = 2.0*iTime + 50.0*mo.x;
    // camera

    float cr = 0.2*cos(0.1*iTime);
    vec3 ro = path( time );
    vec3 ta = path( time+4. );
    ta.y = ro.y;
    gro = ro;

    // build ray
    vec3 ww = normalize( ta - ro);
    vec3 uu = normalize(cross( vec3(sin(cr),cos(cr),0.0), ww ));
    vec3 vv = normalize(cross(ww,uu));
    float r2 = p.x*p.x*0.32 + p.y*p.y;
    p *= (7.0-sqrt(37.5-11.5*r2))/(r2+1.0);
    vec3 rd = normalize( p.x*uu + p.y*vv + 2.5*ww );

    float sun = clamp( dot(sundir,rd), 0.0, 1.0 );
    vec3 col = vec3(0.6,0.71,0.75) - rd.y*0.2*vec3(1.0,0.5,1.0) + 0.15*0.5;
    col += 0.2*vec3(1.0,.6,0.1)*pow( sun, 8.0 );
    col *= 0.95;

    vec3 vos, dir;
    float t = castRay( ro, rd, vos, dir );

    if( t>0.0 ) {
        vec3 nor = -dir*sign(rd);

        vec3 pos = ro + rd*t;
        int mMat = mapMaterial( vos );
        vec3 mpos = mod( pos * 16., 16. );

        if( mMat == 1 ) {
            if( map( vos + vec3(0., 1., 0. ) ) ) {
                mMat = hash(vos) > 0.5?2:4;
                if( map( vos + vec3(0., 2., 0. ) ) ) mMat = 4;
            }
            if ( vos.y < SEALEVEL ) mMat = 9;
        }

        vec3 mCol;
        getMaterialColor( mMat, nor.y!=0.?mpos.xz:nor.x!=0.?-mpos.zy+vec2(32.,32.):-mpos.xy+vec2(32.,32.),mCol );

        // lighting
        float dif = clamp( dot( nor, sundir ), 0.0, 1.0 );
        float sha = 0.0; if( dif>0.01) sha=castVRay(pos+nor*0.01,sundir,32.0);
        float bac = clamp( dot( nor, normalize(sundir*vec3(-1.0,0.0,-1.0)) ), 0.0, 1.0 );
        float sky = 0.5 + 0.5*nor.y;
        float amb = 1.0;//clamp(0.75 + pos.y/100.0,0.0,1.0);

        // ambient occlusion

        vec4 ed = edges( vos, nor, dir );
        vec4 co = corners( vos, nor, dir );
        vec3 uvw = pos - vos;
        vec2 uv = vec2( dot(dir.yzx, uvw), dot(dir.zxy, uvw) );

        float occ = 0.0;
        // (for edges)
        occ += (    uv.x) * ed.x;
        occ += (1.0-uv.x) * ed.y;
        occ += (    uv.y) * ed.z;
        occ += (1.0-uv.y) * ed.w;
        // (for corners)
        occ += (      uv.y *     uv.x ) * co.x*(1.0-ed.x)*(1.0-ed.z);
        occ += (      uv.y *(1.0-uv.x)) * co.y*(1.0-ed.z)*(1.0-ed.y);
        occ += ( (1.0-uv.y)*(1.0-uv.x)) * co.z*(1.0-ed.y)*(1.0-ed.w);
        occ += ( (1.0-uv.y)*     uv.x ) * co.w*(1.0-ed.w)*(1.0-ed.x);
        occ = 1.0 - occ/8.0;
        occ = occ*occ;
        occ = occ*occ;


        vec3 lin = vec3(0.0);
        lin += 4.0*dif*vec3(1.)*(0.5+0.5*occ)*(0.25+0.75*sha);
        lin += 1.8*bac*vec3(1.0,0.5,1.0)*(0.5+0.5*occ);
        lin += 4.0*sky*vec3(0.6,0.71,0.75)*occ;


        if( mMat == 10 ) {
            col = mix( col, mCol*lin*0.6, 0.3);
        } else {
            // atmospheric
            col = mix( mCol*lin*0.2, col, 1.0-exp(-0.0000001*t*t*t) );
        }
    }

    col += 0.2*vec3(1.0,0.4,0.2)*pow( sun, 3.0 );

    // gamma
    col = pow( col, vec3(0.45) );

    // contrast
    col = col* 0.25 + 0.75*col*col*(3.0-2.0*col);

    col = clamp( col, 0.0, 1.0 );

    // vignetting
    col *= 0.5 + 0.5*pow( 16.0*q.x*q.y*(1.0-q.x)*(1.0-q.y), 0.1 );

    fragColor = vec4( col, 1.0 );
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.;
}
