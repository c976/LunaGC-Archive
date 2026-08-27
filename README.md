# hipa-patch
I rewrote and released the rsapatch from my private project `hipa`.  
The SDK's RSA gets disabled (yeah, I was just being lazy — you could replace it by tweaking the hook a bit).

## Why use this patch over others?
The advantage of mine over other patches is that it only relies on [two addresses](http://github.com/kuma-dayo/hipa-patch/blob/main/src/rsapatch.c#L13-L19).  
Various patches are out there, but most of them patch a bunch of addresses that aren't actually necessary for PS.  
My patch is suited for people who only need RSAPatch functionality.
## Usage

1. Build the project
2. Replace the RSA used by ps with `server.pem`
3. Inject the patch
4. Enjoy!

### Tested on

* CNCBWin6.7.50

It may not work on other versions.
