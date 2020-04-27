/* =======================================================
	Copyright 2020 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package eportfolium.com.karuta.webapp.eventbus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class KEventbus
{
	List<KEventHandler> handlerList = new ArrayList<KEventHandler>();

	public KEventbus()
	{
	}

	public boolean processEvent( KEvent event )
	{
		Iterator<KEventHandler> handlerIter = handlerList.iterator();
		while( handlerIter.hasNext() )
		{
			KEventHandler handler = handlerIter.next();
			handler.processEvent(event);
		}
		return true;
	}

	public boolean addChain( KEventHandler handler )
	{
		boolean val = false;
		if( !handlerList.contains(handler) )
		{
			handlerList.add(handler);
			val = true;
		}
		return val;
	}

	public boolean removeChain( KEventHandler handler )
	{
		boolean val = false;
		if( handlerList.contains(handler) )
		{
			handlerList.remove(handler);
			val = true;
		}
		return val;
	}

}
