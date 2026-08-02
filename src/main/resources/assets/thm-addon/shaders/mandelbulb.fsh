#version 330 core
// Adapted from Shadertoy ("Mandelbulb" by evilryu, CC BY-NC-SA 3.0) for THM Addons
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

#define iTime time
#define iResolution resolution
#define iMouse mouse

// --------[ Original ShaderToy begins here ]---------- //
// Created by evilryu
// License Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.


// whether turn on the animation
//#define phase_shift_on

float stime, ctime;
 void ry(inout vec3 p, float a){
     float c,s;vec3 q=p;
      c = cos(a); s = sin(a);
      p.x = c * q.x + s * q.z;
      p.z = -s * q.x + c * q.z;
 }

float pixel_size = 0.0;

/*

z = r*(sin(theta)cos(phi) + i cos(theta) + j sin(theta)sin(phi)

zn+1 = zn^8 +c

z^8 = r^8 * (sin(8*theta)*cos(8*phi) + i cos(8*theta) + j sin(8*theta)*sin(8*theta)

zn+1' = 8 * zn^7 * zn' + 1

*/

vec3 mb(vec3 p) {
    p.xyz = p.xzy;
    vec3 z = p;
    float r = 0.0;
    float dr = 1.0;

    float t0 = 1.0;
    for(int i = 0; i < 7; ++i) {
        r = length(z);
        // ponytail: was `continue`. Once r > 2 nothing in the loop body ever runs again, so z,
        // dr and t0 are frozen and every remaining pass recomputes the identical length(z) -
        // breaking leaves exactly the same state behind and skips the dead iterations.
        if(r > 2.0) break;

        // ponytail: sin/cos of 8*theta and 8*phi by angle-doubling, replacing atan + asin +
        // two sin + two cos per iteration with a sqrt and a handful of multiplies. atan(y/x) is
        // the principal branch, so cos(theta) = |x|/|z.xy| > 0 and sin(theta) = y*sign(x)/|z.xy|
        // exactly (sign taken as +1 at x == 0, which is the atan(+-inf) = +-pi/2 case); asin's
        // range likewise forces cos(phi) >= 0. Assumes phase_shift_on stays off - that variant
        // added iTime to phi before the multiply, which this form can't express. The dead
        // #ifdef was removed rather than left as a trap.
        float L = max(length(z.xy), 1e-20);
        float sx = z.x < 0.0 ? -1.0 : 1.0;
        float ct = abs(z.x) / L;
        float st = z.y * sx / L;
        float sp = z.z / r;
        float cp = sqrt(max(0.0, 1.0 - sp*sp));

        for(int k = 0; k < 3; ++k) { // theta -> 2t -> 4t -> 8t, same for phi
            float ct2 = ct*ct - st*st; st = 2.0*st*ct; ct = ct2;
            float cp2 = cp*cp - sp*sp; sp = 2.0*sp*cp; cp = cp2;
        }

        // ponytail: pow(r,8) and pow(r,7) by repeated squaring - same values, no transcendentals.
        float r2 = r*r, r4 = r2*r2, r7 = r4*r2*r, r8 = r7*r;
        dr = r7 * dr * 8.0 + 1.0;
        r = r8;

        z = r * vec3(ct*cp, st*cp, sp) + p;

        t0 = min(t0, r);
    }
    return vec3(0.5 * log(r) * r / dr, t0, 0.0);
}

 vec3 f(vec3 p){
     ry(p, iTime*0.2);
     return mb(p);
 }


 float softshadow(vec3 ro, vec3 rd, float k ){
     float akuma=1.0,h=0.0;
     float t = 0.01;
     for(int i=0; i < 32; ++i){ // ponytail: was 50; every step is a full 7-iter mandelbulb eval
         h=f(ro+rd*t).x;
         if(h<0.001)return 0.02;
         if(t>6.0) break; // ponytail: ray has left the bulb's ~2-unit bound, nothing left to occlude
         akuma=min(akuma, k*h/t);
          t+=clamp(h,0.01,2.0);
     }
     return akuma;
 }

vec3 nor( in vec3 pos )
{
    vec3 eps = vec3(0.001,0.0,0.0);
    return normalize( vec3(
           f(pos+eps.xyy).x - f(pos-eps.xyy).x,
           f(pos+eps.yxy).x - f(pos-eps.yxy).x,
           f(pos+eps.yyx).x - f(pos-eps.yyx).x ) );
}

vec3 intersect( in vec3 ro, in vec3 rd )
{
    float t = 1.0;
    float res_t = 0.0;
    float res_d = 1000.0;
    vec3 c, res_c;
    float max_error = 1000.0;
    float d = 1.0;
    float pd = 100.0;
    float os = 0.0;
    float step = 0.0;
    float error = 1000.0;

    for( int i=0; i<48; i++ )
    {
        // ponytail: the original spun out all 48 iterations here with an empty then-branch
        // ("avoid broken shader on windows", 2013). Once this condition holds, the body never
        // runs again and so neither error nor t can change - it stays true forever - which
        // makes breaking exactly equivalent and lets converged/escaped rays stop early.
        if( error < pixel_size*0.5 || t > 20.0 ) break;
        {

            c = f(ro + rd*t);
            d = c.x;

            if(d > os)
            {
                os = 0.4 * d*d/pd;
                step = d + os;
                pd = d;
            }
            else
            {
                step =-os; os = 0.0; pd = 100.0; d = 1.0;
            }

            error = d / t;

            if(error < max_error)
            {
                max_error = error;
                res_t = t;
                res_c = c;
            }

            t += step;
        }

    }
    if( t>20.0/* || max_error > pixel_size*/ ) res_t=-1.0;
    return vec3(res_t, res_c.y, res_c.z);
}

 void mainImage( out vec4 fragColor, in vec2 fragCoord )
 {
    vec2 q=fragCoord.xy/iResolution.xy;
     vec2 uv = -1.0 + 2.0*q;
     uv.x*=iResolution.x/iResolution.y;

    pixel_size = 1.0/(iResolution.x * 3.0);
    // camera
     stime=0.7+0.3*sin(iTime*0.4);
     ctime=0.7+0.3*cos(iTime*0.4);

     vec3 ta=vec3(0.0,0.0,0.0);
    vec3 ro = vec3(0.0, 3.*stime*ctime, 3.*(1.-stime*ctime));

     vec3 cf = normalize(ta-ro);
    vec3 cs = normalize(cross(cf,vec3(0.0,1.0,0.0)));
    vec3 cu = normalize(cross(cs,cf));
     vec3 rd = normalize(uv.x*cs + uv.y*cu + 3.0*cf);  // transform from view to world

    vec3 sundir = normalize(vec3(0.1, 0.8, 0.6));
    vec3 sun = vec3(1.64, 1.27, 0.99);
    vec3 skycolor = vec3(0.6, 1.5, 1.0);

    vec3 bg = exp(uv.y-2.0)*vec3(0.4, 1.6, 1.0);

    float halo=clamp(dot(normalize(vec3(-ro.x, -ro.y, -ro.z)), rd), 0.0, 1.0);
    vec3 col=bg+vec3(1.0,0.8,0.4)*pow(halo,17.0);


    float t=0.0;
    vec3 p=ro;

    vec3 res = intersect(ro, rd);
     if(res.x > 0.0){
           p = ro + res.x * rd;
           vec3 n=nor(p);
           float shadow = softshadow(p, sundir, 10.0 );

           float dif = max(0.0, dot(n, sundir));
           float sky = 0.6 + 0.4 * max(0.0, dot(n, vec3(0.0, 1.0, 0.0)));
            float bac = max(0.3 + 0.7 * dot(vec3(-sundir.x, -1.0, -sundir.z), n), 0.0);
           float spe = max(0.0, pow(clamp(dot(sundir, reflect(rd, n)), 0.0, 1.0), 10.0));

           vec3 lin = 4.5 * sun * dif * shadow;
           lin += 0.8 * bac * sun;
           lin += 0.6 * sky * skycolor*shadow;
           lin += 3.0 * spe * shadow;

           res.y = pow(clamp(res.y, 0.0, 1.0), 0.55);
           vec3 tc0 = 0.5 + 0.5 * sin(3.0 + 4.2 * res.y + vec3(0.0, 0.5, 1.0));
           col = lin *vec3(0.9, 0.8, 0.6) *  0.2 * tc0;
            col=mix(col,bg, 1.0-exp(-0.001*res.x*res.x));
    }

    // post
    col=pow(clamp(col,0.0,1.0),vec3(0.45));
    col=col*0.6+0.4*col*col*(3.0-2.0*col);  // contrast
    col=mix(col, vec3(dot(col, vec3(0.33))), -0.5);  // satuation
    col*=0.5+0.5*pow(16.0*q.x*q.y*(1.0-q.x)*(1.0-q.y),0.7);  // vigneting
     fragColor = vec4(col.xyz, smoothstep(0.55, .76, 1.-res.x/5.));
 }
// --------[ Original ShaderToy ends here ]---------- //

void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.;
}
