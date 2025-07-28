package net.oneki.mtac.model.resource;

public class ResourceType {
  // the type is used to identify the type of resource
  // it is stored in the resource table and used to order resources by type
  // This is mainly used in a global search to return the most important resources first
  // The lower the number, the more important the resource is

  // Types that are public and could be displayed in the UI
	public static final int PUBLIC_RESOURCE = 100;

	// Types that are internal and normally should not be displayed in the UI
  public static final int INTERNAL_RESOURCE = 1000;
}
