#! /usr/bin/perl -w

use utf8;
use strict;
use open qw/:std :encoding(utf8)/;

my %want;
my $file;
my $idx = 'a';

open $file,'<','specialmessages.java' or die;
for my $line (<$file>)
{
  if($line =~ /\/\* JOSM trac newticket page \*\/ tr\("(.*)"\);/)
  {
    my $res = $1;
    $want{$res} = $idx++;
  }
  close($file);
}
for my $po (reverse sort glob("po/*.po"))
{
  local $/;
  $/ = "\n\n";
  open $file,'<',$po or die;
  my %lang;
  
  for my $line (<$file>)
  {
    $line =~ s/"\n"//g;
    next if $line =~ /# fuzzy/;
    for my $t (keys %want)
    {
      if($line =~ /msgid "\Q$t\E"/ && $line =~ /msgstr "(.+)"/)
      {
        my $res = $1;
        $res =~ s/\\"/"/g;
        $res =~ s/''/’/g;
        $res =~ s/'/’/g;
        $lang{$want{$t}} = $res;
      }
    }
  }
  if(scalar(%lang) == scalar(%want))
  {
    $po =~ /^po\/(.*)\.po$/;
    my $code = $1;
    if($code =~ /en_/)
    {
      my $diff = 0;
      for my $t (sort keys %lang)
      {
        my %wantr = reverse %want;
        my $r = $lang{$t};
        $r =~ s/\"/\\"/g;
        if($wantr{$t} ne $r)
        {
          ++$diff;
        }
      }
      next if !$diff;
      print "$diff\n";
    }
    print "  } else if (navigator.language.indexOf('$code') == 0) {\n";
    for my $t (sort keys %lang)
    {
      print "    $t = '$lang{$t}';\n";
    }
  }
}
