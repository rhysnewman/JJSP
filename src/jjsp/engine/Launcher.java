
/*
JJSP - Java and Javascript Server Pages 
Copyright (C) 2016 Mind Foundry Ltd

This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by 
the Free Software Foundation, either version 3 of the License, or 
(at your option) any later version.

This program is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
for more details.

You should have received a copy of the GNU General Public License along with 
this program. If not, see http://www.gnu.org/licenses/.
*/
package jjsp.engine;

import java.io.*;
import java.net.*;
import java.util.*;

import jjsp.jde.*;
import jjsp.util.*;

public class Launcher
{
    public static void main(String[] args) throws Exception
    {
        Args.parse(args);
        if (Args.getBoolean("nogui", false) || Args.getBoolean("server", false))
            Engine.main(args);
        else
            JDE.main(args);
    }
}
