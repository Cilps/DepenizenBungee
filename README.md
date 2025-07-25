# DepenizenBungee (forked)

**Disclaimer: this is NOT the official DepenizenBungee, please see the [official DepenizenBungee](https://github.com/DenizenScript/DepenizenBungee) for the original project.**


This DepenizenBungee fork changes the 'authentication' system. I made it because I couldn't get DepenizenBungee working on my Pterodactyl (Docker) setup.

This different 'authentication' system relies on a `config.yml` file instead of relying on the BungeeCord config 'authenticate' servers.

### When to use this
Please only use this if you know what you are doing. Otherwise, just use the official [DepenizenBungee](https://github.com/DenizenScript/DepenizenBungee).

### Example config
```yaml
Allowed Servers:
  # Name of the server (keep this the same as in your Bungeecord config)
  lobby:
    ip: 172.18.0.1
    port: 25566
```

### Licensing pre-note:

This is an open source project, provided entirely freely, for everyone to use and contribute to.

If you make any changes that could benefit the community as a whole, please contribute upstream.

### The short of the license is:

You can do basically whatever you want, except you may not hold any developer liable for what you do with the software.

### The long version of the license follows:

The MIT License (MIT)

Copyright (c) 2019-2023 The Denizen Script Team

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
