---
title: Passwordless SSH login
layout: website-normal
---
To enable passwordless SSH login to a *nix server, first you will need a pair of keys. If you don't already have a keypair generated you'll first of all need to create one.
To generate a new keypair you run the following command:

    your-user@host1:~$ ssh-keygen -t rsa

This will prompt you for a location to save the keys, and a pass-phrase:

    Generating public/private rsa key pair.
    Enter file in which to save the key (/home/skx/.ssh/id_rsa): 
    Enter passphrase (empty for no passphrase): 
    Enter same passphrase again: 
    Your identification has been saved in /home/skx/.ssh/id_rsa.
    Your public key has been saved in /home/skx/.ssh/id_rsa.pub.

Then, assuming that you want to enable passwordless SSH login to the `host2` server from `host1` with the `id_rsa` and `id_rsa.pub` files you've just generated you should run the following command:

    your-user@host1:~$ ssh-copy-id -i ~/.ssh/id_rsa.pub username@host2

If `host1` doesn't have `ssh-copy-id` installed, you can either install `ssh-copy-id` or manually copy the `id_rsa.pub` key to the `host2` by issuing the following commands:

    host1# cat ~/.ssh/id_rsa.pub | ssh user@host2 'cat >> .ssh/authorized_keys'

or if you need to make a `.ssh` directory on `host2`

    host1#cat ~/.ssh/id_rsa.pub | ssh user@host2 'mkdir .ssh; chmod 700 .ssh; cat >> .ssh/authorized_keys; chmod 644 .ssh/authorized_keys'
